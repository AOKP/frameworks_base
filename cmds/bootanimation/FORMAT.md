# bootanimation format

## zipfile paths

The system selects a boot animation zipfile from the following locations, in order:

    /system/media/bootanimation-encrypted.zip (if getprop("vold.decrypt") = '1')
    /system/media/bootanimation.zip
    /oem/media/bootanimation.zip

## zipfile layout

The `bootanimation.zip` archive file includes:

    desc.txt - a text file
    part0  \
    part1   \  directories full of PNG frames
    ...     /
    partN  /

## desc.txt format

The first line defines the general parameters of the animation:

    WIDTH HEIGHT FPS

  * **WIDTH:** animation width (pixels)
  * **HEIGHT:** animation height (pixels)
  * **FPS:** frames per second, e.g. 60

It is followed by a number of rows of the form:

    TYPE COUNT PAUSE PATH [#RGBHEX CLOCK]

  * **TYPE:** a single char indicating what type of animation segment this is:
      + `p` -- this part will play unless interrupted by the end of the boot
      + `c` -- this part will play to completion, no matter what
  * **COUNT:** how many times to play the animation, or 0 to loop forever until boot is complete
  * **PAUSE:** number of FRAMES to delay after this part ends
  * **PATH:** directory in which to find the frames for this part (e.g. `part0`)
  * **RGBHEX:** _(OPTIONAL)_ a background color, specified as `#RRGGBB`
  * **CLOCK:** _(OPTIONAL)_ the y-coordinate at which to draw the current time (for watches)

There is also a special TYPE, `$SYSTEM`, that loads `/system/media/bootanimation.zip`
and plays that.

## loading and playing frames

Each part is scanned and loaded directly from the zip archive. Within a part directory, every file
(except `trim.txt` and `audio.wav`; see next sections) is expected to be a PNG file that represents
one frame in that part (at the specified resolution). For this reason it is important that frames be
named sequentially (e.g. `part000.png`, `part001.png`, ...) and added to the zip archive in that
order.

## trim.txt

To save on memory, textures may be trimmed by their background color.  trim.txt sequentially lists
the trim output for each frame in its directory, so the frames may be properly positioned.
Output should be of the form: `WxH+X+Y`. Example:

    713x165+388+914
    708x152+388+912
    707x139+388+911
    649x92+388+910

If the file is not present, each frame is assumed to be the same size as the animation.

## audio.wav

Each part may optionally play a `wav` sample when it starts. To enable this, add a file
with the name `audio.wav` in the part directory.

## exiting

The system will end the boot animation (first completing any incomplete or even entirely unplayed
parts that are of type `c`) when the system is finished booting. (This is accomplished by setting
the system property `service.bootanim.exit` to a nonzero string.)

## protips

### PNG compression

Use `zopflipng` if you have it, otherwise `pngcrush` will do. e.g.:

    for fn in *.png ; do
        zopflipng -m ${fn}s ${fn}s.new && mv -f ${fn}s.new ${fn}
        # or: pngcrush -q ....
    done

Some animations benefit from being reduced to 256 colors:

    pngquant --force --ext .png *.png
    # alternatively: mogrify -colors 256 anim-tmp/*/*.png

### creating the ZIP archive

    cd <path-to-pieces>
    zip -0qry -i \*.txt \*.png \*.wav @ ../bootanimation.zip *.txt part*

Note that the ZIP archive is not actually compressed! The PNG files are already as compressed
as they can reasonably get, and there is unlikely to be any redundancy between files.
