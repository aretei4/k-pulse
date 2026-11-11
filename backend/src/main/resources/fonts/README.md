# PDF fonts

Drop **`NotoSansOriya-Regular.ttf`** in this folder and the downloaded voter
list (FR-U13) prints Odia names with it.

Without it, the application looks for an Odia family the host already has —
Noto Sans Oriya, Lohit Odia, Utkal, Kalinga or Nirmala UI first, then anything
else holding Odia glyphs. Only when the host has none of those do Odia names
go missing from the file entirely (the built-in Helvetica drops the characters
rather than drawing boxes), and the log says so once at startup.

The file is deliberately not committed: it is a third-party font, and it is
about 150 KB of binary in a repository that otherwise holds none. Get it from
Google Fonts (`https://fonts.google.com/noto/specimen/Noto+Sans+Oriya`, SIL
Open Font License) and put it here under exactly that name.

A deployment that would rather not bundle it can point at a font the host
already has, by file or by installed family name:

```properties
kpulse.pdf.odia-font=/usr/share/fonts/truetype/noto/NotoSansOriya-Regular.ttf
kpulse.pdf.odia-font=Nirmala UI
```

Whatever is used must hold Odia glyphs; one that does not is refused at startup
with a line in the log rather than printing boxes silently.

To be told rather than guessed for — so a server missing the font fails loudly
instead of printing in whatever family it happens to carry — switch the search
off:

```properties
kpulse.pdf.odia-font-host-fallback=false
```

> The font is not simply embedded in the PDF. An Odia name is stored in a
> different order from the way it is written — the vowel sign in ବେ is stored
> after its consonant and printed before it — and a PDF writer does no such
> reordering. Names are therefore laid out by the font's own shaping rules and
> drawn as outlines; see `PdfFontSource` for the detail.
