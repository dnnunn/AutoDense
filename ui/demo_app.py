
import streamlit as st
from uploader import render_uploader, info_table

st.set_page_config(page_title="Uploader Demo", layout="centered")
st.title("Uploader demo: multi-file + ZIP, ASCII-safe, resettable")

records = render_uploader(
    label="Upload gel images / CSVs / PDFs",
    allowed_exts=["png","jpg","jpeg","csv","tsv","pdf"],
    allow_zip=True,
    accept_multiple_files=True,
    help_text="You can drag multiple files here. For a folder, zip it first.",
    key_root="gel_demo",
)

st.subheader("Summary")
if records:
    info_table(records)

    # Example: separate images from tables
    imgs = [r for r in records if r.safe_name.lower().endswith(('.png','.jpg','.jpeg'))]
    tsvs = [r for r in records if r.safe_name.lower().endswith(('.csv','.tsv'))]

    st.write(f"Images: {len(imgs)} | Tables: {len(tsvs)} | Total: {len(records)}")

    # Preview first image if present
    if imgs:
        st.image(imgs[0].bytes, caption=imgs[0].original_name)

    # Show how you'd persist to disk (disabled by default)
    with st.expander("How to save files to disk", expanded=False):
        st.code(
            "\n".join([
                "import os",
                "save_dir = 'uploads'",
                "os.makedirs(save_dir, exist_ok=True)",
                "for r in records:",
                "    with open(os.path.join(save_dir, r.safe_name), 'wb') as f:",
                "        f.write(r.bytes)",
                "print('Saved', len(records), 'files to', save_dir)",
            ]),
            language="python",
        )
else:
    st.info("Upload some files to see details.")
