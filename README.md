# GenesisPDF

Offline Android PDF editor project.

## Current implementation (0.1)
- Open existing PDFs with Android's document picker.
- Render and navigate PDF pages.
- Build a debug APK through GitHub Actions.

## Not implemented yet
- Editing existing text, selecting text objects, changing fonts, inserting text and saving modified PDFs.

**Important:** Drawing a white rectangle over old text does not remove the original PDF text and is not a secure redaction. Real content-stream editing requires careful handling of PDF operators, fonts, encodings, text layout and scanned pages. Do not advertise this prototype as a full PDF text editor.

## Build
GitHub Actions > Build GenesisPDF APK > Run workflow. Download the GenesisPDF-debug artifact after a successful run.
