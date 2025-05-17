# TuneUp Karaoke Application

TuneUp is a desktop karaoke application designed to provide users with a straightforward platform for enjoying karaoke sessions. Its primary purpose is to play audio files while displaying synchronized lyrics from corresponding .lrc files, enabling users to sing along effectively.

## Song Files

To use the application, MP3 audio files and their corresponding .lrc lyric files must be placed in the `songs` directory at the root of the project.

## Features

* **Media Playback:** Plays MP3 audio tracks.
* **Karaoke Lyrics Support:** Loads, parses, and displays synchronized lyrics from .lrc files.
* **Song Library Management:**
  * Browse and search the song library by title, artist, or genre.
  * Queue songs for playback.
* **Playback Control:** Standard controls including play, pause, stop, skip, and a progress bar for seeking.
* **Lyrics Display and Synchronization:**
  * Lyrics synchronized with audio playback.
  * Current lyric line highlighting.
  * Manual adjustment of lyrics timing offset (saved for future sessions).
* **Display Customization:**
  * Fullscreen mode for lyrics display.
  * Light and Dark theme options for the lyrics screen.
* **User Interface:** Intuitive JavaFX interface for navigation and control.

## Limitations

* **Fixed Content Library:** The application operates with a predefined, fixed set of songs and .lrc files. Users cannot add, remove, or modify the core song/lyric library content.
* **No In-App Metadata Editing:** Song metadata (title, artist, genre) cannot be edited within the application.
* **No Playlist Creation:** Does not support user-created or saved playlists.
* **No Vocal Effects or Adjustments:** Does not include features like vocal removal, key adjustment, or tempo changes.
* **Queue Persistence:** The song queue is not saved between application sessions; each session starts with an empty queue.
