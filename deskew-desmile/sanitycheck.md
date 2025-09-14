Quick sanity notes:

* **Scope:** This patch never touches your downstream algorithms; it only changes which image the user interacts with and which image Analysis consumes by default.
* **Safeguard:** If `cv2` isn’t importable, the sliders disable and the original image flows through unchanged, so nothing breaks.
* **State keys used:** `res_uploaded_image` (existing), new persistent keys `cal_rotate_deg`, `cal_shear_deg`, `cal_desmile_px`, and two images `res_original_image` (first upload snapshot) and `res_cal_image` (geometry-corrected).

Test it in three taps: upload → expand the pre-cal panel → nudge rotate/shear/desmile → calibrate lanes. Then hop to Analysis; it will automatically use the corrected image. If you later want a toggle to analyze the raw image, just add a checkbox that switches `work_img` back to `res_uploaded_image`.

Where to take this next: we can add a dashed guide overlay that previews band horizontality after desmile—handy for dialing in curvature with feedback.
