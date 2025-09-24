

# ImageCarousel

This is my solution to the Shutterfly interview project  I would like to summarize things here.

# Assumptions on requirements
There are two ways to interpret the requirements:
- Once an image has been placed on the canvas the image can be resized within the canvas by using pinching.  This is an alternate way to interpret zoom.  Panning involves moving the image 
around the canvas by dragging it.
- Once an image has been placed on the canvas the image zooming would involve pinching the image modifying the zoom level of the image within the given bounds of the image as placed
on the canvas.  Panning would be done by moving the zoomed image within the current image boundaries in order to show a different portion of the image.  Moving the image within
the canvas is done by long pressing the image.

The first listed interpretation was chosen as the most likely requirement based upon the wording that the aspect ratio of the image must be maintained while zooming though this
is still slightly ambiguous.  This is the solution on the main branch

There are two branches in the repository:
- Resize/move is in the main branch
- Zoom/pan is in the branch zoom-pan

# Using the app

Two different apk files have been provided in the apk directory:
- move-resize.apk has the first solution.
-- The image can be resized within the canvas and moved by dragging the image.  The image cannot be moved outside of the canvas area.
-- Pinching the image will resize the image larger or smaller.  The image cannot be expanded larger than the canvas.
-- The image cannot be removed from the canvas and changing the image layer order is not possible.

- pan-zoom.apk has the second solution.  
-- Once an image is on the canvas it has the same height and width as the original bitmap.  Zooming can be accomplished by pinching the image.
-- Once an image is zoomed it can be panned within the image frame.
-- Once an image is on the canvas it can be moved around the canvas by long pressing it and then moving it once the preview is shown.  It will also be
  brought to the front of the image layers as the images can be overlayed on top of each other.  This is done so that moving an image does not conflict
  with zooming or panning.

For both version of the app
- Images cannot be removed from the canvas once they have been placed.
- In order to make the carousel easily scrollable the user must long press on a carousel image in order to place the image on the canvas.
- Images do not disappear from the carousel when placed on the canvas.  Multiples of the same image can be put on the canvas.
- The app locks itself in whatever orientation the device is in when the app starts.  If you start the app in portrait it will lock that way and
the same with landscape.  This is held until the main activity closes.  This is done in order to support both portrait and landscape but the 
math necessary to translate the images on the canvas into the new orientation is beyond the scope of this project.  Device rotation was not
called out specifically as a requirement so I am acknowledging that it can be done but it being considered out of scope.


# The solution utilizes the following:
* Repository pattern
* DI using Hilt
* MVVM
* Clean Architecture
* Flow
* Jetpack Compose
* Unit testing using JUnit and Mockito

I use the repository pattern to demonstrate support for scalability and modularity.  The project loads images from the assets directory
but also has a stub for loading from the device media storage, though this is not implemented.

The data is very simple but in a more robust project there would be more metadata associated with images so the DTO pattern is used to isolate
the data objects from the domain objects.  This is unnecessary for this data but I wanted to show how this would be implemented.

There is a single use case that loads the images from the repository.  This is done done to support testability and avoid unnecessary bloat in the view model.
It utilizes flow in order to support loading, error and image loaded states.  This is also not required as failure to load from the assets directory is not going to
happen but it demonstrates using flow to support the UI state.

The viewmodel class loads the images from the repository and maintains a list of those images.  It also maintains a list of the images that have been put on the canvas.
These images are kept in a new data class in order to support unique ID, offset, scale, etc.  It provides methods to modify the content and placement data of those objects.
It will be the source of truth for the carousel as well as the canvas.  This would be important if the app could change orientation but it cannot.  In order to support device 
rotation the optimal size of the canvas would need to be established at app start so that the dropped images would be in the correct location once the device is rotated.  The
complexity of this is beyond the scope of this project.  

There is a UiState class that is used to keep separation of the domain state and the UI state.  This is also unnecessary for a project this simple but is considered
good architectural practice in a more complex scenario.

The image carousel and canvas are each in their own composables.  

Two unit tests have been added for the repository and the use case.  These tests provide 100% coverage for these classes.  No UI tests have been added.