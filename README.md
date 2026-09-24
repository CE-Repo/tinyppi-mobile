# 📱 TinyPPI — Mobile

An Android app for [TinyPPI](https://github.com/CE-Repo/script.tinyppi): the
playback information the add-on draws over a running video on the television,
on the phone instead — every reading, the Dolby Vision metadata view, the
luminance chart, the event log, and the transport and VS10 controls. Built with
Jetpack Compose and Material 3, and it speaks the whole of the add-on's own web
API: the event stream with its delta frames, the history, the artwork, and both
command endpoints.

---

## Table of Contents

1. [What It Does](#1-what-it-does)
2. [Requirements](#2-requirements)
3. [Setting It Up](#3-setting-it-up)
4. [Two Addresses, One App](#4-two-addresses-one-app)
5. [The Screens](#5-the-screens)
6. [What of the API Is Used](#6-what-of-the-api-is-used)
7. [Deltas, or Why It Costs Nothing](#7-deltas-or-why-it-costs-nothing)
8. [Building It](#8-building-it)
9. [Project Layout](#9-project-layout)
10. [Troubleshooting](#10-troubleshooting)
11. [License](#11-license)

---

## 1. What It Does

- **Everything the overlay prints**, in the cards it prints them in. None of it
  is composed here: the add-on renders each row the way the on-screen overlay
  draws it — same formatting, same units, same labels, translated through
  Kodi's own string table — so a row that gains a unit on the television gains
  it here, and neither can drift from the other.
- **What is playing**, with its poster, its year and genre, how the source is
  graded and what is actually leaving the box. A conversion gets a badge of its
  own, because that is the one thing on the screen that is a decision rather
  than a reading.
- **The Dolby Vision metadata view**: the same list the overlay's second window
  is built from, trim tables and all.
- **The luminance chart and the event log** the box keeps for the running
  title — the peak the grade ever reached, every output switch, every frame
  that was lost. A phone that opens halfway through a film gets the whole
  picture, not the part it was connected for.
- **The player**, on a box that allows it: play, stop, seek, volume, mute,
  audio and subtitle tracks, and the VS10 conversions the source can be put
  through.

---

## 2. Requirements

| | |
|---|---|
| **Android** | 8.0 (API 26) or newer |
| **On the box** | [`script.tinyppi`](https://github.com/CE-Repo/script.tinyppi) installed in Kodi, with its **web server switched on** in the add-on settings |
| **Network** | The phone has to be able to reach the box — the same network, or a way in published for it |

The web server is **off** until it is switched on in the add-on's settings.
Nothing here works until it is.

---

## 3. Setting It Up

1. In Kodi: **Add-ons → TinyPPI → Settings → Web**. Switch the server on. It
   shows the address it is listening on and an eight-character token.
2. In the app: **Settings → Local address**. Type the host and the port (the
   add-on's default is **8099**), then the token.
   Pasting `http://192.168.1.10:8099/` into the host field fills the port and
   the HTTPS switch too.
3. Press **Test**. A box that answers says which version it is, whether the
   token was accepted, and whether it will let a client switch anything.
4. Press **Save**.

The token is upper-cased as it is typed: the add-on mints it out of an alphabet
with no `I`, `O`, `0` or `1` in it, because a token is read off a television and
typed on a phone.

Two things in the add-on's own settings decide what the app can show:

- **Control** — off, the transport row and the VS10 buttons are not drawn at
  all. The add-on sends no track lists to a client that may not switch tracks.
- **Metadata** — off, the Dolby Vision metadata screen stays empty even for a
  Dolby Vision source.

---

## 4. Two Addresses, One App

Two addresses are stored, because the box under the television and the box from
a train are the same box at two different addresses: a different scheme, a
different port, usually a different token, when the way in from outside is a
reverse proxy of its own.

| Mode | What happens |
|---|---|
| **Automatic** | The local address first; the remote one the moment it does not answer. |
| **Local only** | Never reach for the address from outside. |
| **Remote only** | Always go the way in that was published. |

Two details make automatic mode worth using rather than merely available:

- **A short leash.** Every address but the last gets two seconds to connect. A
  box on the same network answers in milliseconds; one that is not on this
  network answers nothing at all, and the full six-second timeout spent finding
  that out is time the screen stays empty for.
- **A note that survives the session.** Which address last answered is written
  to disk and read again on the next cold start, so a launch away from home
  goes straight to the address that works. It goes stale after four hours —
  long enough for an evening out, short enough that the next morning starts at
  home again.

Only a transport failure moves on to the next address. An answer is an answer:
a 401 from the local box means the token is wrong, and asking the remote one
with the same wrong token would turn a clear error into a confusing one.

---

## 5. The Screens

### Live

The title with its poster, the source and output badges, the video and audio
formats named in badges of their own, the progress, and — on a box that allows
it — the transport row, the track pickers and the VS10 conversions.

While something is playing on such a box, the phone's own volume buttons turn
the box up and down instead of the phone; a switch under *Readings* in the
settings hands them back. Every key that acts on the box gives a short tick
under the thumb, as far as the system's touch-feedback setting allows.

The measurements at the bottom are the numeric half of a snapshot: the readings
the dashboard charts rather than prints. The luminance pair only exists inside
a Dolby Vision RPU, so it is absent for every other grade rather than shown as
zeroes.

With nothing playing, the screen says so and nothing more. What could be
playing instead has two places of its own in the bar — see **Films and
Series** below — which are open whether or not the box is doing anything.

A film the box left half-watched carries a bar along the bottom of its poster
and is resumed where it was, the same as pressing it in Kodi's own window; one
already seen wears a tick in the corner of the picture. What **IMDb** made of it — or TMDb where IMDb has nothing to say — sits in the opposite corner of the poster, and is left off entirely where neither house has an opinion. Under the title
are the year and how long the film runs. The wall carries a
search box, which narrows it on this phone rather than asking the box again per
keystroke; the cross inside it empties the field again. The posters are fetched
as they are scrolled to and each one crosses the network once, so a library of
five hundred costs the dozen on screen.

Under the films is the same wall again for the box's **series library**. A
series is not something that can be put on — an episode is — so a tap on a
poster does not start anything: it opens the show, and the screen becomes that
show's episodes, in the order they were made and each season folded away under
its own heading — all of them folded, so a show that has run for nine years
arrives as a dozen lines rather than as several hundred rows. Each heading says
how many episodes the season holds and how long they run altogether. A tap opens a
season, a tap on one of its episodes starts it, and the way back sits at the
top of the list.

Each poster carries the number of episodes still unwatched in the corner a
watched film wears its tick in; a show seen right through wears the tick
instead. What **IMDb** made of it — or TMDb where IMDb has nothing to say — sits in the opposite corner of the poster, and is left off entirely where neither house has an opinion. Each episode row says how long that episode runs beside the
number it is. The episodes of a show are read when that show is opened and not
before, so a house with ninety series in it is not sent every episode of all of
them to draw a wall of ninety posters.

A box that offers neither shelf — the cards switched off in the add-on's
settings, control switched off, or an empty video database — keeps the line
saying nothing is playing. The two cards have a setting each, so it can offer
the films and not the series, or the other way round.

### Films and Series

The same two shelves, as places of their own in the bar.

What the live screen offers is the offer you come across: nothing is on, so
here is what could be. These are the shelves gone looking for — open at any
time, including while a film is running, because somebody deciding what to put
on next should not have to stop what is on to go and look.

They are the same walls and the same state: one reading of the library serves
whichever screen is drawing it, so changing tabs asks the box nothing it has
already answered. A shelf the box has said it will not offer loses its place in
the bar rather than leaving a tab that leads to an apology.

They keep themselves up to date without being asked. Every snapshot carries the
version the box's library is on, and that number moves whenever what the
shelves would say moves — a film watched to the end, one switched off in the
middle, a scan that added a series — so a wall that is open when a title ends
reads itself again there and then. It no longer takes restarting the app to see
a tick appear on something that was watched last night, and a series somebody
is inside stays open across it: the episodes are read again in place, with the
seasons left unfolded as they were.

Both are cards that fold under their heading like the live screen's, and are
found the way they were left: **Continue watching** and the wall on each. A
folded card is its heading and how much is on it.

**Continue watching** sits at the very top of both: the films the box was
stopped in the middle of at the top of Films, the episodes at the top of
Series, the last one seen first, on a row that scrolls sideways. Each poster
carries how far the box got and the same rating badge as the walls — a film
its own, an episode its show's — and a tap resumes it where it was. An episode
stands on it as its show — the show's poster and name, with which episode it is
underneath — so an episode of a show you have not opened in the app is one tap
away too. The row is read on the same occasions the shelves are, steps aside
while you search, and its card is not there at all when nothing is
half-watched.

Under it is **Recently added**: the ten films that arrived in the library last,
and on Series the ten shows that gained an episode last (Kodi dates a show by
its newest episode), newest first, on a row that scrolls sideways like the one
above it. The tiles are the walls' own and a tap on one does what it does
there. The row is taken from the list the wall is drawn from, so it moves with
it; it steps aside while you search, and is not there with an add-on too old to
send the date.

Under those is a card on each: **Unwatched
films**, the films without a tick, and **Unwatched series**, the shows with an
episode still waiting, again on a row that scrolls sideways; **All films** and
**All series** come after it. Same tiles, same search, same tap; it folds and
is remembered like the others, and is not there at all once everything has
been seen, or while a search finds nothing unwatched.

**A tap on** a film, a series, an episode or a Continue watching tile opens a
dialog with three options: **Play** (**Resume** where the box has a point to
resume from), **Mark as watched** and **Mark as unwatched**. A series cannot be
played, so its first option is **Open**, which shows its episodes. Marking is
written into Kodi's own library — a series marked either way is every episode
of it — and every shelf is read again at once, so the tick and the unwatched
cards show what the library now holds.

A film or an episode that was stopped part-way through gets two more options:
**Play from the beginning**, which starts it from the top instead of resuming,
and **Clear resume point**, which forgets where it got to — taking it off
Continue watching — while leaving it watched or unwatched as it was.

**Pulling either shelf down** reads it again anyway. It is the answer when the
box was off while the app was looking at it, or when its library was switched
on in Kodi after the app had already been told there was none — a pull overrules
every reason the app otherwise has for not asking. Inside a show it reads that
show's episodes as well as the shelf behind them. A shelf with nothing on it can
be pulled too, which is the one most worth pulling.

It is also the only read the app says anything about when it fails. Everywhere
else a wall is an offer, and an offer that cannot be made is not worth
interrupting anybody over; a pull was asked for, so a box that cannot answer it
says so along the bottom of the screen rather than leaving a gesture that
seemed to do nothing.

Opening a series gives its episodes a **cover** to stand under — the picture
of it at the shape a television is, rather than the poster it was picked off
the wall by — and then its seasons. The system's own back gesture closes the
show and puts the wall back, which is one tab short of where it would otherwise
have gone.

### Arranging the cards

**A long press on the heading of any card** — on Live, Metadata, Films, Series
or History — opens that screen for arranging: every card on it as one row,
with arrows to move it up or down and an eye to take it off the screen or put
it back. **Reset** puts that screen back the way the app draws it; **Done**, or
the back gesture, returns to the cards. The order and the removed cards are
remembered per screen, by the card's own name rather than its heading, so they
survive a change of language. A card that only comes with some titles — the
VS10 card, the HDR10 panel — keeps its place for when it comes back, and a card
the app has never shown before arrives next to the one it follows by default.
A screen whose cards have all been removed says so, with the way back to them.
**Settings → Cards → Reset all cards** puts every screen back at once.

### Readings

Every row the overlay prints, grouped into the panels it groups them into. A
row the stream does not carry is left out by the add-on rather than sent blank,
and a whole card whose source cannot carry it goes the same way — which is what
keeps it readable on a phone.

### Metadata

The Dolby Vision metadata view. Trim tables scroll sideways rather than wrap: a
trim pass is read down its columns, and a row that wraps onto a second line
breaks exactly that.

### History

The scene luminance over one minute, ten, or the whole hour the box holds, plus
the event list — output switches, display-mode changes, track changes, cache
dips, temperature and processor warnings, frame-rate changes.

Fetched rather than streamed, and only when there is something new in it: the
snapshot carries an event counter, and a number that has not moved means the
hour of samples already on screen is still the whole story.

### Settings

The two addresses, which one to use, how the readings arrive, the
appearance, and a card that puts every screen's cards back where they started.

---

## 6. What of the API Is Used

All of it.

| Endpoint | Used for |
|---|---|
| `GET /api/hello` | The connection test. Unauthenticated on the far side, which is what lets the test tell "nothing there" from "wrong token". |
| `GET /api/stream` | The live connection. One per app, shared by every screen. |
| `GET /api/state` | The polling fallback, and the second half of the connection test. |
| `GET /api/history` | The chart and the event list. |
| `GET /api/art` | The poster of what is playing, the posters on both walls, and an episode's still. |
| `GET /api/library` | The films the box has, for the wall shown while nothing is playing. |
| `GET /api/series` | The series it has, for the wall under them. |
| `GET /api/episodes` | The episodes of one series, read when it is opened. |
| `GET /api/continue` | The films and episodes left half-watched, for the row at the top of both shelves. |
| `POST /api/mode` | The VS10 buttons. |
| `POST /api/command` | Play/pause, stop, previous/next chapter, seek, seek to a percentage, volume up/down, mute, audio track, subtitle track. |
| `POST /api/play` | Starting one of the library's films, or one episode of a series — resumed, or from the beginning. |
| `POST /api/watched` | Marking a film, a series or an episode as watched or unwatched. |
| `POST /api/resume` | Clearing the resume point of a film or an episode. |

The token travels as `X-TinyPPI-Token` on everything Retrofit sends, and as
`?token=` on the event stream and the artwork — the two the add-on documents
that form for, because a browser can put no header on an `EventSource` or on an
`<img>`.

**One stream per phone.** The add-on caps concurrent streams at six and fans the
same snapshot out to each of them, so four screens opening four connections
would spend four of those six slots showing the same thing. The connection is
shared across every screen and dropped a few seconds after the last one stops
looking, so a backgrounded app holds none.

---

## 7. Deltas, or Why It Costs Nothing

The add-on sends one whole snapshot when a stream opens and only what moved
after that. A whole one is tens of kilobytes and most of it stands still for two
hours; a delta is a few dozen bytes, which on a phone is the difference between
a connection that costs nothing and one that costs a battery.

Folding those back into whole snapshots happens in
[`SnapshotMerge`](app/src/main/java/com/jamal2367/tinyppimobile/data/remote/SnapshotMerge.kt),
on the JSON rather than on the model — a delta names keys, and a model that has
already turned an absent key into a default can no longer tell *unchanged* from
*gone*. The two long lists are patched row by row while their shape holds and
replaced outright when it does not, because rows cannot be written into a card
the client has not been told about yet.

It is the one piece of this app that can be wrong without failing: a merge that
drops a key leaves a screen showing a reading from two minutes ago and reports
nothing. So it is tested twice.

[`SnapshotMergeTest`](app/src/test/java/com/jamal2367/tinyppimobile/SnapshotMergeTest.kt)
checks each branch on its own. [`DeltaFixtureTest`](app/src/test/java/com/jamal2367/tinyppimobile/DeltaFixtureTest.kt)
checks the property that actually matters —

```
merge(previous, delta(previous, current)) == current
```

— against frames the add-on itself cut. `tools/generate-delta-fixtures.py` lifts
`_snapshot_delta` verbatim out of `web/server.py` (the delta block needs nothing
from Kodi), runs it over pairs of realistic snapshots, and writes each `current`
out beside the delta it produced. So the test compares the two implementations
rather than comparing one of them with an idea of the other:

```bash
python tools/generate-delta-fixtures.py /path/to/script.tinyppi
```

The six pairs between them reach every branch the add-on has: readings moving
alone, rows patched by id, the whole card list replaced because its shape
changed, metadata patched by position, playback ending — which deletes a dozen
keys at once — and a new title bringing them all back. A test guards that too,
so a generator that quietly stopped producing row patches fails rather than
leaving the suite passing on a third of the fold.

---

## 8. Building It

```bash
git clone https://github.com/CE-Repo/tinyppi-mobile.git
cd tinyppi-mobile
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

| Task | What it does |
|---|---|
| `./gradlew testDebugUnitTest` | The unit tests |
| `./gradlew lintRelease` | Lint on the configuration that ships |
| `./gradlew assembleRelease` | The shrunk, obfuscated APK |

**Signing.** A release build looks for signing material in the environment
first (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), then
in a gitignored `keystore.properties` at the repository root:

```properties
storeFile=release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

With neither, `assembleRelease` falls back to the debug key rather than
failing — an unsigned APK is one nobody can do anything with.

**Releases** are cut by hand from the Actions tab. Every run that gets through
the tests and lint publishes its APK as a release tagged `build-N`, and the app
checks that tag once per launch to tell you it is behind.

---

## 9. Project Layout

```
app/src/main/java/com/jamal2367/tinyppimobile/
├── data/
│   ├── model/          The add-on's payloads as Kotlin: Snapshot, History, Library, commands
│   ├── prefs/          The two addresses and everything else remembered on disk
│   ├── remote/         Retrofit interface, failover, the event stream, the delta merge
│   └── repository/     LiveSession (stream + polling fallback), PlayerRepository
├── di/                 AppContainer — the whole graph, by hand
├── ui/
│   ├── components/     Cards, badges, the status line, the chart
│   ├── live/           What is playing, what can be done to it, and the walls
│   ├── library/        The two shelves, as screens of their own
│   ├── details/        The overlay's own rows
│   ├── metadata/       The Dolby Vision metadata view
│   ├── history/        The chart and the event list
│   ├── settings/       The two addresses and the rest
│   ├── navigation/     Routes and the nav host
│   └── theme/          Material 3 Expressive, colours, type, shapes
└── util/               Formatters, artwork URLs, error messages

app/src/test/           Unit tests, and the delta fixtures they run against
tools/                  The script that cuts those fixtures out of the add-on
```

The graph is built by hand in `AppContainer` rather than by a framework: the
whole network layer is smaller than the machinery a framework would add to it.

---

## 10. Troubleshooting

**Nothing answers, and the status line says *Offline*.**
The add-on's web server is off until it is switched on in Kodi. Check that
first — it is the usual answer. After that, check the port: the add-on picks
its own and defaults to 8099, not to Kodi's own web port.

**"Token refused".**
`/api/hello` needs no token, so a box can be perfectly reachable and still
refuse every reading. Compare the token against the one under the add-on's
web-server settings; generating a new one there invalidates whatever was handed
out before.

**"Box busy".**
Every stream slot is taken. The add-on allows six, and a browser tab left open
on the dashboard holds one for as long as it is open. The app keeps asking on a
timer meanwhile, so the readings still arrive — just not as they happen.

**The transport row and the VS10 buttons are missing.**
Control is off in the add-on's settings. The add-on sends no track lists to a
client that may not switch tracks, so there would be nothing under the buttons
anyway.

**The metadata screen is empty on a Dolby Vision film.**
The metadata view has a setting of its own in the add-on, separate from the
web server. Only a Dolby Vision source has an RPU to walk in the first place.

**Everything times out, on Android 17 or newer.**
Local network access is a permission of its own from API 37 on, and an app that
was never granted it has its connections dropped rather than refused — which
arrives six seconds later looking exactly like a box that is switched off. The
app asks at startup; if it was denied, grant it under **Settings → Apps →
TinyPPI → Permissions**.

**The poster is missing.**
Most of what a box plays has no library entry and therefore no poster. The
add-on sends an empty tag for it and the app draws its stand-in.

---

## 11. License

MIT. See [LICENSE](LICENSE).
