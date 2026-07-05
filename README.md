<div align="center">

# 📷 AGallery

**A fast, private, offline-first gallery for your photos & videos — no ads, no accounts, no cloud.**

[![Download](https://img.shields.io/badge/Download-Latest%20APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](../../releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![License: Noncommercial](https://img.shields.io/badge/License-PolyForm%20Noncommercial%201.0.0-3DA639?style=for-the-badge)](./LICENSE)
[![Release](https://img.shields.io/github/v/release/andreasmbngaol/AGallery?style=for-the-badge&color=blue)](../../releases)

</div>

> 🛠️ **For developers:** this README is written for people who just want to *use* the app. The full technical documentation (architecture, building, signing, releasing) lives in the **[Documentation site](https://andreasmbngaol.github.io/AGallery/)** (source under [`/docs`](./docs)).

---

## What is AGallery?

AGallery is a clean, lightweight gallery app for Android. It shows the photos and
videos already on your phone — nothing is uploaded anywhere, there are no ads,
and you don't need to sign in. It's built to feel smooth and modern, with a
"liquid-glass" navigation bar and a full-screen viewer that you can pinch to
zoom.

**In short:** your photos, your phone, nobody else. 🔒

---

## ✨ What you can do

- **Browse everything** — all your photos and videos in one fast, smooth grid.
- **Albums** — automatic albums (Recent, Camera, Videos, Screenshots, Screen recordings, Favorites) plus your own folders.
- **Create a new album** — tap **➕** on the Albums tab, name it, and pick which photos to add.
- **Favorites** — mark the photos you love and find them in one place.
- **Full-screen viewer** — tap any photo to open it, then **pinch to zoom**. Videos play right inside the app.
- **Select many at once** — in an album, tap **Select** to pick multiple items, then **Copy**, **Move**, or **Delete** them in one go.
- **Copy / Move to album** — pick a destination from a visual grid of albums (with cover thumbnails), or make a new one on the spot.
- **Recycle bin (Trash)** — deleted items go to Trash first, so you can **restore** them. They're cleaned up automatically after 30 days.
- **Share & set as wallpaper** — straight from the photo viewer.
- **Sort** — flip between newest-first and oldest-first.
- **Make it yours** — adjust grid size and the look of the app (solid / frosted / glass styles) in Settings.

---

## 📥 Install

1. Go to the **[Releases page](../../releases/latest)**.
2. Download the latest `app-release.apk`.
3. Open the file on your phone and allow installing from your browser/files app if prompted.
4. Launch **AGallery** and grant photo access when asked.

> **Requirements:** Android 8.0 (Oreo) or newer.

---

## 🔐 Permissions & privacy

AGallery only asks for access to your **photos and videos** — that's the one
thing it needs to show your gallery. On Android 14+ you can even choose to share
**only selected** photos, and the app respects that.

- ❌ No internet permission used for tracking — your library never leaves your device.
- ❌ No account, no sign-in, no cloud sync.
- ❌ No ads, no analytics SDKs.
- ✅ Everything is read directly from your phone's own media storage.

When you delete something, Android shows its own confirmation dialog — AGallery
can't delete your files without your explicit "yes".

---

## 🚀 Quick start

1. **Grant media access** on first launch.
2. **Browse** the grid on the **Gallery** tab. Pull down to refresh.
3. **Switch tabs** — Settings · Gallery · Albums — using the floating glass bar (or swipe).
4. **Tap** a photo to open it full-screen, then **pinch to zoom**. Tap a video to play it.
5. **Long-press** a photo for quick actions (favorite, move to trash, etc.).
6. On the **Albums** tab, tap **➕** to create an album, or open an album and tap **Select** to copy/move/delete several items at once.

---

## ❓ FAQ

**Does it upload my photos anywhere?**
No. AGallery is 100% offline. Your photos stay on your device.

**Where do deleted photos go?**
To the in-app **Trash**, where you can restore them. They're permanently removed automatically after 30 days (or immediately if you choose "delete permanently").

**Can I edit photos?**
Not at the moment — AGallery is focused on being a great *viewer* and organizer.

**Is there a search feature?**
Not yet — it's planned for a future update.

**Can I use it freely?**
The source is publicly available, and it's free for **personal and other
noncommercial use**. Commercial use by others isn't permitted — see the License
section below.

---

## 📚 Documentation (for developers)

Want to build it yourself, understand the architecture, or cut a signed release?
Everything technical lives in the separate documentation site:

👉 **[AGallery Documentation](https://andreasmbngaol.github.io/AGallery/)** — or read the Markdown sources in [`/docs`](./docs):

- [Overview & tech stack](./docs/index.md)
- [Architecture](./docs/architecture.md)
- [Building from source](./docs/building.md)
- [Releasing & signing](./docs/releasing.md)
- [Third-party licenses](./docs/third-party-licenses.md)

---

## 🙌 Credits

AGallery is built on excellent open-source libraries. See
**[THIRD-PARTY-NOTICES.md](./THIRD-PARTY-NOTICES.md)** for the full list and
their licenses. Icons are from [Phosphor Icons](https://phosphoricons.com) (MIT).

---

## 📄 License

AGallery is released under the **PolyForm Noncommercial License 1.0.0**. This is
a *source-available* license: you're free to use, study, modify, and share the
software for **any noncommercial purpose**, as long as the copyright notice is
kept. **Commercial use by others is not permitted** without a separate license
from the author. See the [LICENSE](./LICENSE) file for the full terms.

> Note: because it restricts commercial use, this is technically a
> "source-available" license rather than an OSI-approved "open source" one. As
> the copyright holder, the author retains full rights to use AGallery
> commercially or to grant commercial licenses.

© 2026 andreasmlbngaol
