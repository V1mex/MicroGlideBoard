# MicroGlideBoard — Experimental Android Keyboard

A gesture-based keyboard that replaces individual key taps with two-thumb directional input — one thumb picks a letter group, the other picks the letter.

## How It Works

MicroGlideBoard divides the keyboard into two interaction areas. The left thumb selects a letter group; while it's held, the right thumb moves in one of eight directions to enter the corresponding letter. The result is a typing method based on relative movement rather than tapping individual keys.

## Features

- Two-thumb, gesture-based text input
- Directional letter selection across multiple letter groups
- Ukrainian, English, and russian keyboard layouts
- Repeated-letter input and gesture-based deletion
- Dedicated symbol keyboard with 2 pages
- Long-press symbol alternatives where supported
- Visual letter previews and directional hints
- Configurable keyboard behavior and gesture-related settings
- Custom-drawn keyboard interface

## Gestures

- **Left thumb — letter groups:** Touch the left area to activate a group. Move vertically to switch groups.
- **Right thumb — letter selection:** While the left thumb is active, move in one of eight directions to enter a letter.
- **Space:** Release the right thumb.
- **Repeat last letter:** Move the left thumb to the right of the active group.
- **Delete:** Move the left thumb to the left of the active group. Hold to repeat.
- **Symbols:** Switch to the symbol keyboard using the assigned gesture. Two pages, modelled after Gboard, with long-press alternatives where supported.

Getting comfortable with the input method takes some practice and settings tuning.

## Installation

1. Install MicroGlideBoard on your Android device.
2. Open keyboard or language settings.
3. Enable MicroGlideBoard under available input methods.
4. Select it as the active keyboard.

Exact menu names vary by device.

## Status

The MVP is functional. The project is not actively maintained and is not intended as a daily-driver keyboard — it's an experiment in alternative typing ergonomics. Contributions, forks, and experiments are welcome under the project license.

## Screenshots

#### Idle state
<img width="362" height="309" alt="зображення" src="https://github.com/user-attachments/assets/b5091d0e-6ab8-4a07-849c-3c5a86909d1b" />

#### Typing state
<img width="362" height="309" alt="зображення" src="https://github.com/user-attachments/assets/fccb2363-2c3b-4acf-8753-e8a0aef17317" />

#### Symbol keyboard
<img width="362" height="309" alt="зображення" src="https://github.com/user-attachments/assets/7b0154e9-f7e0-45eb-a841-024e63491c2f" />

#### Settings
<img width="233" height="514" alt="зображення" src="https://github.com/user-attachments/assets/9ff10a29-7170-45a5-b04f-77dc15b3b3e2" />
<img width="233" height="514" alt="зображення" src="https://github.com/user-attachments/assets/224f1113-3146-41f1-95f3-26ad89dc71de" />

## License

GNU General Public License v3.0 — see [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html) for full terms.
