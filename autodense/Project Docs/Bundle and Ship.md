# Ship strategy (what to bundle vs. fetch)

- **Bundle the core app separately from models.**
   Make a lean, signed, notarized `.app` (Fiji + your plugin + llama-server) ≈ 300–500 MB.
   Put models in a **separate “Models Pack” DMG/PKG**. Users can:
  - Install it once (offline capable, can distribute on USB in labs), or
  - Let a built‑in **Model Manager** download/verify the model on first run.
- **Install model outside the app bundle.**
   Put GGUFs under `~/Library/Application Support/Gel Densitometer/models/`.
   Benefits: you can update/swap models **without re‑signing/notarizing the app**, and you avoid bloating app updates.
- **Offer per‑arch builds, not universal.**
   Ship **Apple Silicon (arm64)** and **Intel (x86_64)** builds separately.
   Universal binaries double your llama-server size; separate builds halve the download and improve performance predictability.

# Notarization, size, and updates

- **Notarization**: Large apps/DMGs are fine. Apple notarizes multi‑GB images; it just takes longer. Using `notarytool --wait` is normal.
- **Updates**: Use an external updater (e.g., Sparkle) for the **core app**; your **Model Manager** handles model updates independently with checksums. No full‑app re‑download when models change.

# First‑run UX that keeps labs happy

- Launch app → detect missing models → show **Model Manager**:
  - “Install Gemma‑3 4B Vision (≈1.8–2.5 GB)”
  - “Install smaller quick model (≈1.0–1.5 GB)”
  - “I already have a model… (Choose Folder)”
- Show progress, **resume‑safe** downloads, SHA‑256 verification, and a storage location note.
- Allow **lab admins** to pre‑seed models in `/Library/Application Support/Gel Densitometer/models/` for all users.

# Performance & footprint tips

- **Quantization**: Prefer **Q5_K_M** (quality) or **Q4_K_M** (smaller) for text; for vision, test Q4 vs Q5 and pick the smallest that stays reliable on your gels/QC prompts.
- **Context window**: Keep prompts tight; 4–8 k context is plenty for command parsing/QC.
- **Memory**: 4B vision models run fine on 8–16 GB Macs; 7–8B can need 16–24 GB. Provide a “Model suitability” note in the chooser.

# Distribution choices

- **Outside Mac App Store** (recommended):
   Fewer sandboxing constraints (you’re spawning a local server), easier to ship Java/Fiji, easier model management. Sign + notarize, deliver DMG on your website or internal portal.

- **Mac App Store**: Possible but higher friction (sandboxing, JRE bundling, background processes). You’d likely need to replace the helper process approach and request specific entitlements. For most labs, outside‑store is smoother.

  ## 1) Where models live

  - **Per‑user** (default):
     `~/Library/Application Support/Gel Densitometer/models/`
  - **System‑wide** (admin pre‑seed / PKG):
     `/Library/Application Support/Gel Densitometer/models/`

  Your app should look **first** in the user dir, then fall back to the system dir.

  ------

  ## 2) Java: Model Manager (ready to use)

  Create these files in `plugin/src/main/java/com/yourco/gel/models/`.

  ### `ModelOption.java`

  ```
  package com.yourco.gel.models;
  
  import java.net.URI;
  
  public record ModelOption(
          String id,               // e.g., "gemma3-4b-vision-q5"
          String displayName,      // e.g., "Gemma 3 4B Vision (Q5)"
          URI downloadUri,         // full https URI to GGUF (or zip)
          String sha256,           // lowercase hex
          long sizeBytes,          // expected size after download
          String filename          // local filename to save
  ) {}
  ```

  ### `Sha256.java`

  ```
  package com.yourco.gel.models;
  
  import java.io.InputStream;
  import java.nio.file.Files;
  import java.nio.file.Path;
  import java.security.MessageDigest;
  
  public final class Sha256 {
      private Sha256(){}
  
      public static String ofFile(Path p) throws Exception {
          MessageDigest md = MessageDigest.getInstance("SHA-256");
          try (InputStream in = Files.newInputStream(p)) {
              byte[] buf = new byte[1 << 20];
              int n;
              while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
          }
          return toHex(md.digest());
      }
  
      private static String toHex(byte[] b) {
          StringBuilder sb = new StringBuilder(b.length * 2);
          for (byte x : b) sb.append(String.format("%02x", x));
          return sb.toString();
      }
  }
  ```

  ### `ResumeDownloader.java`  (resume + progress callback)

  ```
  package com.yourco.gel.models;
  
  import java.io.IOException;
  import java.io.InputStream;
  import java.io.OutputStream;
  import java.net.*;
  import java.net.http.*;
  import java.nio.file.*;
  import java.time.Duration;
  import java.util.concurrent.atomic.AtomicLong;
  
  public final class ResumeDownloader {
  
      public interface Progress {
          void onProgress(long downloaded, long total);
      }
  
      private final HttpClient http = HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(10))
              .followRedirects(HttpClient.Redirect.NORMAL)
              .build();
  
      public Path downloadWithResume(URI uri, Path target, long expectedSize, Progress progress) throws IOException, InterruptedException {
          Files.createDirectories(target.getParent());
          Path tmp = target.resolveSibling(target.getFileName() + ".part");
  
          long existing = Files.exists(tmp) ? Files.size(tmp) : 0L;
          if (existing > 0 && existing > expectedSize) Files.delete(tmp); // corrupted partial
  
          HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                  .timeout(Duration.ofMinutes(30));
          if (existing > 0) b.header("Range", "bytes=" + existing + "-");
          HttpRequest req = b.GET().build();
  
          HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
          int code = resp.statusCode();
          if (code != 200 && code != 206) {
              throw new IOException("HTTP " + code + " for " + uri);
          }
          long total = expectedSize;
          AtomicLong downloaded = new AtomicLong(existing);
  
          try (InputStream in = resp.body();
               OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
              byte[] buf = new byte[1 << 20];
              int n;
              long lastTick = System.nanoTime();
              while ((n = in.read(buf)) > 0) {
                  out.write(buf, 0, n);
                  long d = downloaded.addAndGet(n);
                  long now = System.nanoTime();
                  if (progress != null && (now - lastTick) > 100_000_000L) { // ~100ms
                      progress.onProgress(d, total);
                      lastTick = now;
                  }
              }
          }
          Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
          return target;
      }
  }
  ```

  ### `ModelManager.java` (selection logic + verification)

  ```
  package com.yourco.gel.models;
  
  import javax.swing.*;
  import java.awt.*;
  import java.io.IOException;
  import java.net.URI;
  import java.nio.file.*;
  import java.util.List;
  
  public final class ModelManager {
  
      public record Locations(Path userDir, Path systemDir) {
          public Path[] searchOrder() { return new Path[]{ userDir, systemDir }; }
      }
  
      private final Locations loc;
      private final ResumeDownloader dl = new ResumeDownloader();
  
      public ModelManager() {
          this.loc = new Locations(
                  Path.of(System.getProperty("user.home"),
                          "Library", "Application Support", "Gel Densitometer", "models"),
                  Path.of("/", "Library", "Application Support", "Gel Densitometer", "models")
          );
      }
  
      public Locations locations() { return loc; }
  
      /** Return first existing file named filename in search order, else null. */
      public Path findInstalled(String filename) {
          for (Path base : loc.searchOrder()) {
              Path p = base.resolve(filename);
              if (Files.isRegularFile(p)) return p;
          }
          return null;
      }
  
      public boolean verifySha256(Path file, String expectedHex) {
          try {
              String got = Sha256.ofFile(file);
              return got.equalsIgnoreCase(expectedHex);
          } catch (Exception e) {
              return false;
          }
      }
  
      /** Ensure model exists and passes checksum; if missing, prompt UI to install. */
      public Path ensureInstalled(Component parent, ModelOption option, boolean preferUserDir) throws Exception {
          Path existing = findInstalled(option.filename());
          if (existing != null && verifySha256(existing, option.sha256())) return existing;
  
          Path base = preferUserDir ? loc.userDir : loc.systemDir; // systemDir may need admin; prefer userDir
          Files.createDirectories(base);
          Path target = base.resolve(option.filename());
  
          int choice = JOptionPane.showConfirmDialog(parent,
                  """
                  The required model is not installed:
                  %s (%.2f GB)
  
                  Install now to:
                  %s
                  """.formatted(option.displayName(), option.sizeBytes() / (1024.0*1024*1024),
                          target.toAbsolutePath()),
                  "Install Model", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
          if (choice != JOptionPane.OK_OPTION) throw new RuntimeException("Model installation cancelled.");
  
          JDialog progressDlg = new JDialog(SwingUtilities.getWindowAncestor(parent), "Downloading model…", Dialog.ModalityType.APPLICATION_MODAL);
          JProgressBar bar = new JProgressBar();
          bar.setStringPainted(true);
          JLabel lbl = new JLabel("Starting…");
          JPanel panel = new JPanel(new BorderLayout(8,8));
          panel.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
          panel.add(lbl, BorderLayout.NORTH);
          panel.add(bar, BorderLayout.CENTER);
          progressDlg.getContentPane().add(panel);
          progressDlg.setSize(480, 120);
          progressDlg.setLocationRelativeTo(parent);
  
          SwingWorker<Path, Void> worker = new SwingWorker<>() {
              @Override protected Path doInBackground() throws Exception {
                  return dl.downloadWithResume(option.downloadUri(), target, option.sizeBytes(), (d, t) -> {
                      int pct = (t > 0) ? (int)Math.min(100, (d * 100.0 / t)) : 0;
                      SwingUtilities.invokeLater(() -> {
                          bar.setValue(pct);
                          bar.setString("%d%% (%.2f / %.2f GB)".formatted(pct, d/(1024.0*1024*1024.0), t/(1024.0*1024*1024.0)));
                          lbl.setText("Downloading: " + option.displayName());
                      });
                  });
              }
          };
          worker.execute();
          progressDlg.setVisible(true); // blocks until disposed elsewhere
          Path downloaded;
          try {
              downloaded = worker.get();
          } catch (Exception ex) {
              progressDlg.dispose();
              throw ex;
          }
          progressDlg.dispose();
  
          if (!verifySha256(downloaded, option.sha256())) {
              Files.deleteIfExists(downloaded);
              throw new IOException("Checksum verification failed for " + downloaded);
          }
          return downloaded;
      }
  
      /** Simple chooser UI: pick one of the provided options and ensure it’s installed. */
      public Path pickAndInstall(Component parent, List<ModelOption> options) throws Exception {
          Object sel = JOptionPane.showInputDialog(parent,
                  "Select a model to install:",
                  "Model Manager", JOptionPane.PLAIN_MESSAGE, null,
                  options.stream().map(ModelOption::displayName).toArray(),
                  options.get(0).displayName());
          if (sel == null) throw new RuntimeException("Model selection cancelled.");
          ModelOption choice = options.stream().filter(o -> o.displayName().equals(sel)).findFirst().orElse(options.get(0));
          return ensureInstalled(parent, choice, true);
      }
  }
  ```

  ### Usage (from your startup / first use of LLM)

  ```
  ModelManager mm = new ModelManager();
  List<ModelOption> choices = List.of(
      new ModelOption("gemma3-4b-vision-q5", "Gemma 3 4B Vision (Q5)",
          URI.create("https://example.com/models/gemma-3-4b-vision-q5.gguf"),
          "PUT_SHA256_HEX_HERE", 2_350_000_000L, "gemma-3-4b-vision-q5.gguf"),
      new ModelOption("gemma3-4b-vision-q4", "Gemma 3 4B Vision (Q4, smaller)",
          URI.create("https://example.com/models/gemma-3-4b-vision-q4.gguf"),
          "PUT_SHA256_HEX_HERE", 1_850_000_000L, "gemma-3-4b-vision-q4.gguf")
  );
  
  // try to auto-find a compatible installed model first:
  Path model = mm.findInstalled("gemma-3-4b-vision-q5.gguf");
  if (model == null) {
      // prompt user to pick & install
      model = mm.pickAndInstall(null, choices);
  }
  // now `model` is a verified local file path
  ```

  ------

  ## 3) Optional: “Bring your own model” button

  If users have a local GGUF already:

  ```
  JFileChooser fc = new JFileChooser();
  fc.setDialogTitle("Select GGUF model");
  if (fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
      Path p = fc.getSelectedFile().toPath();
      // optionally verify hash against a known list, or accept without hash
      // copy into Application Support to standardize:
      Files.createDirectories(mm.locations().userDir());
      Path dest = mm.locations().userDir().resolve(p.getFileName().toString());
      Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
  }
  ```

  ------

  ## 4) PKG: “Models Pack” installer (admin pre‑seed)

  This builds a signed **.pkg** that drops models into `/Library/Application Support/Gel Densitometer/models`.

  ### Folder layout (on your build machine)

  ```
  models-pkg/
    payload/
      Library/Application Support/Gel Densitometer/models/
        gemma-3-4b-vision-q5.gguf
        gemma-3-4b-vision-q4.gguf
    scripts/
      postinstall
  ```

  ### `scripts/postinstall`

  ```
  #!/bin/bash
  set -e
  MODEL_DIR="/Library/Application Support/Gel Densitometer/models"
  chmod -R 755 "$MODEL_DIR" || true
  chown -R root:wheel "$MODEL_DIR" || true
  exit 0
  ```

  Make it executable:

  ```
  chmod +x models-pkg/scripts/postinstall
  ```

  ### Build the component package

  ```
  pkgbuild \
    --identifier com.yourco.gel.models \
    --version 1.0.0 \
    --root "models-pkg/payload" \
    --scripts "models-pkg/scripts" \
    --install-location "/" \
    GelDensitometerModels.pkg
  ```

  ### (Optional) Product archive with distribution XML

  If you want a prettier installer (single page, license text), create `distribution.xml` and:

  ```
  productbuild \
    --distribution distribution.xml \
    --resources resources \
    --package-path . \
    GelDensitometerModels-1.0.0.pkg
  ```

  ### Sign & notarize (recommended if you distribute broadly)

  ```
  codesign --force --sign "Developer ID Installer: YOUR NAME (TEAMID)" GelDensitometerModels.pkg
  xcrun notarytool submit GelDensitometerModels.pkg --apple-id "you@appleid.com" --team-id TEAMID --password "app-specific-password" --wait
  xcrun stapler staple GelDensitometerModels.pkg
  ```

  Users (or IT) can now install via double‑click or:

  ```
  sudo installer -pkg GelDensitometerModels.pkg -target /
  ```

  Your app will find those models automatically (system dir).

  ------

  ## 5) Nice‑to‑have polish

  - **SHA‑256 manifest**: ship a small JSON (id → {sha256,size,filename,uri}) your Model Manager can load for updates.
  - **Download resume across launches**: the `.part` file already makes this work.
  - **Multi‑model support**: store user’s last choice in a tiny config file:
     `~/Library/Application Support/Gel Densitometer/config.json`.
  - **Disk space check**: warn if free space < model size + 1 GB.

  ------

  ## 6) One‑liner integration in your app

  On first attempt to use NL or Vision QC:

  ```
  ModelManager mm = new ModelManager();
  Path modelPath = mm.findInstalled("gemma-3-4b-vision-q5.gguf");
  if (modelPath == null) modelPath = mm.pickAndInstall(mainWindow, modelChoices);
  startLlamaServerWith(modelPath); // your existing launcher
  ```

  That’s it: production‑ready model management with verification and an optional admin PKG for labs.