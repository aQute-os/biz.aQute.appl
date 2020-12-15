package biz.aQute.drone.litchi.csv.provider;

public class ViewSync {
/**
 * An example:
	5090,
	-33.54948434838393,
	150.99967479537557,
	523818.98127554677194,
	-0.28372808067457,
	0.00000000000000,
	0.00000000000000,
	63454496156,63454496156,

1 - incrementing integer counter
2 - latitude in decimalZZ
3 - longitude in decimal
4 - altitude in metres
5 - ?
6 - ?
7 - ?
8 - ?
9 - ?

I'm sure there are other 'cont
 */
	
	/**
	 * https://github.com/LiquidGalaxy/liquid-galaxy/wiki/GoogleEarth_ViewSync
	 * Some of our Liquid Galaxy installations have a podium with a touch screen we use for controlling Google Earth.  To make that work, we added a feature to Google Earth to let us control it by writing a simple text file.

```
  ViewSync/queryFile = /tmp/query.txt
```

When the feature is turned on, Google Earth will poll the filesystem to look for the specified file, read it, then delete it.  Note that ViewSync/send must also be set to "true".

On a multi-user host system make sure the queryFile is owned by the user running Google Earth. Otherwise it won't get deleted and will be continually re-read!

## Search

If the contents of the file are of the form "`search=some search string`", it performs a search on "`some search string`" just as if you had typed it into the Search box in Google Earth, then flies to the nearest result.

## Switching Planets

If the contents of the file are of the form "`planet=earth`" (or `mars` or `moon`), Google Earth will switch to the specified planet view.

The contents "search=_lat_,_long_" still work as expected for that planet.

## FlyTo

In addition to simply sending the view to a lat,long the KML for a LookAt can be used with "flytoview=". For example, to send the view to San Francisco use something like -

```
flytoview=<LookAt><longitude>-122.4017881321627</longitude><latitude>37.79152911640639</latitude><altitude>0</altitude><heading>167.0211046386626</heading><tilt>68.68179673613697</tilt><range>774.4323347622752</range><altitudeMode>relativeToGround</altitudeMode><gx:altitudeMode>relativeToSeaFloor</gx:altitudeMode></LookAt>
```

## Controlling Tours

Tours can be started and stopped with "playtour=" and "exittour=".

playtour= expects the name of a tour, i.e. the contents of the "name" element of a "gx:Tour" in a currently loaded kml:

```
playtour=My World Tour
```

Where the tour kml looks like:

```
...
<gx:Tour>
<name>My World Tour</name>
...
```

exittour= accepts "true", and will always stop a currently running tour:

```
exittour=true
```
	 */
}
