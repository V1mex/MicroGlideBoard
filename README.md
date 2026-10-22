# MicroGlideBoard — Experimental Android Keyboard

MicroGlideBoard is an experimental Android keyboard that explores an alternative approach to mobile typing using two-thumb directional gestures instead of conventional individual letter keys.

The project focuses on reducing thumb movement and exploring a different approach to keyboard ergonomics.

## Project Overview

MicroGlideBoard uses a custom keyboard interface divided into two main interaction areas. One thumb selects a group of letters, while the other selects an individual letter using directional gestures.

The project was largely developed with the assistance of AI to quickly prototype and test an unconventional typing concept. Its implementation, gesture recognition, and interface are experimental.

## Current Status

- Created primarily for personal testing and experimentation, and for anyone interested in alternative keyboard designs.
- Development is currently frozen.
- The MVP is functional, but the project is not intended to be a polished daily-driver keyboard.
- Gesture recognition and text input may have limitations and require practice.
- Available layouts: Ukrainian, English, and Russian.

## Installation

1. Install MicroGlideBoard on your Android device.
2. Open your device's keyboard or language settings.
3. Enable MicroGlideBoard under the available input methods.
4. Select MicroGlideBoard as your active keyboard.

The exact names and locations of these settings vary between Android devices.

**Note:** As with any third-party keyboard, MicroGlideBoard needs to be enabled and selected in Android's input method settings before it can be used for typing.

## How to Use

MicroGlideBoard uses two-thumb gesture-based input.

- **Left thumb — letter groups:** Touch the left area to activate a letter group. Move vertically to select a different group.
- **Right thumb — letter selection:** While the left thumb is active, move the right thumb in one of eight directions to enter the corresponding letter.
- **Space:** Releasing the right thumb inserts a space.
- **Repeat the last letter:** Move the left thumb to the right of the active group.
- **Delete:** Move the left thumb to the left of the active group. Holding the gesture activates repeated deletion.
- **Symbols:** Switch to the symbol keyboard using the assigned gesture. The symbol layout includes 2 pages, mimics the Gboard design and long-press alternatives where supported.

The keyboard relies on relative movement and directional gestures rather than tapping individual letter keys. Getting comfortable with the input method may take some experimentation, settings adjustments and practice.

## Features

- Two-thumb, gesture-based text input.
- Directional letter selection across multiple letter groups.
- Ukrainian, English, and russian keyboard layouts.
- Repeated-letter input and gesture-based deletion.
- Dedicated symbol keyboard with 2 pages.
- Long-press symbol alternatives where supported.
- Visual letter previews and directional hints.
- Configurable keyboard behavior and gesture-related settings.
- Custom-drawn keyboard interface.

## Development

MicroGlideBoard is a personal experimental project rather than a production-ready keyboard. Its purpose is to explore an alternative approach to mobile typing, not to compete feature-for-feature with established keyboards.

The implementation is relatively lightweight and uses native Android input method functionality with a custom interface.


Contributions, forks, and experiments based on the project are welcome, provided they comply with the project license.

## License

MicroGlideBoard is distributed under the GNU General Public License v3.0 (GPL-3.0).

Redistributed and modified versions must comply with the terms of the license.

See the [GNU GPL v3.0](https://www.gnu.org/licenses/gpl-3.0.html) for the full license text.