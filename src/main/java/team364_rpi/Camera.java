package team364_rpi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.ArrayList;
import java.util.List;
import edu.wpi.first.vision.VisionPipeline;
import org.opencv.core.Mat;
import org.opencv.core.*;
import org.opencv.imgproc.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import edu.wpi.cscore.MjpegServer;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.cscore.VideoCamera;
import edu.wpi.cscore.VideoSource;
import edu.wpi.cscore.VideoMode.PixelFormat;
import edu.wpi.cscore.CvSink;
import edu.wpi.cscore.CvSource;
import edu.wpi.first.cameraserver.CameraServer;

public class Camera {
    private static String configFile = "/home/pi/configReadByJAR.json";// "/boot/frc.json";

    public static class CameraConfig {
        public String name;
        public String path;
        public JsonObject config;
        public JsonElement streamConfig;
    }

    public static int team;
    public static List<CameraConfig> cameraConfigs = new ArrayList<>();

    public Object imgLock = new Object();
    private Thread visionThread;
    private DynamicVisionPipeline pipeline;
    //public static final int cameraResX = 320;
    //public static final int cameraResY = 240;

    public Camera() {
        enableVisionThread(); // outputs a processed feed to the dashboard (overlays the found boiler tape)
    }

    public void enableVisionThread() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        pipeline = new DynamicVisionPipeline();
        Mat mat = new Mat();
        
        CameraServer inst = CameraServer.getInstance();
        readConfigFile();

        // CameraStuff.CameraConfig newConfig = new CameraStuff.CameraConfig();
        // newConfig.name = "cam0";
        // newConfig.path = "/dev/video0";
        VideoSource cam = prepareCamera(cameraConfigs.get(0));
        inst.startAutomaticCapture(cam);

        MjpegServer server = CameraServer.getInstance().addServer("test");

        // System.out.println("getVideo: "+
        // CameraServer.getInstance().getVideo(cam).getSource());

        //     CameraServer.getInstance().addCamera(cam);

        // System.out.println(CameraServer.getInstance().)
        CvSink inputStream = inst.getVideo(cam);// .getVideo(); //capture mats from camera
        CvSource outputStream = inst.putVideo("Processed Image", 1024, 576);
        server.setSource(outputStream);

        // inputStream.grabFrame(mat);
        // System.out.println("Grabbed frame: " + mat.size());

        // CvSink inputStream = CameraServer.getInstance().getVideo();//.getVideo();
        // //capture mats from camera

        // server.setSource(cam);

        // outputStream.putFrame(mat); // send steam to CameraServer
        // server.setSource(outputStream);

        // MjpegServer server = new MjpegServer(arg0, arg1, arg2);

        // Mat mat = new Mat(); //define mat in order to reuse it

        // runProcessing = true;

        visionThread = new Thread(() -> {

            while (!Thread.interrupted()) { // this should only be false when thread is disabled

                if (inputStream.grabFrame(mat) == 0) { // fill mat with image from camera)
                    //outputStream.notifyError(inputStream.getError()); // send an error instead of the mat
                    continue; // skip to the next iteration of the thread
                }
                System.out.println("Grabbed Frame");
                outputStream.putFrame(mat); // give (and CameraServer) a new frame
            }

        });
        visionThread.setDaemon(true);
        visionThread.start();
    }

    /**
     * Report parse error.
     */
    public static void parseError(String str) {
        System.err.println("config error in '" + configFile + "': " + str);
    }

    /**
     * Read single camera configuration.
     */
    public static boolean readCameraConfig(JsonObject config) {
        CameraConfig cam = new CameraConfig();

        // name
        JsonElement nameElement = config.get("name");
        if (nameElement == null) {
            parseError("could not read camera name");
            return false;
        }
        cam.name = nameElement.getAsString();

        // path
        JsonElement pathElement = config.get("path");
        if (pathElement == null) {
            parseError("camera '" + cam.name + "': could not read path");
            return false;
        }
        cam.path = pathElement.getAsString();

        // stream properties
        cam.streamConfig = config.get("stream");

        cam.config = config;

        cameraConfigs.add(cam);
        return true;
    }

    public static void setConfigFile(String inConfig) {
        configFile = inConfig;
    }

    /**
     * Read configuration file.
     */
    public static boolean readConfigFile() {
        // parse file
        JsonElement top;
        try {
            top = new JsonParser().parse(Files.newBufferedReader(Paths.get(configFile)));
        } catch (IOException ex) {
            System.err.println("could not open '" + configFile + "': " + ex);
            return false;
        }

        // top level must be an object
        if (!top.isJsonObject()) {
            parseError("must be JSON object");
            return false;
        }
        JsonObject obj = top.getAsJsonObject();

        // team number
        JsonElement teamElement = obj.get("team");
        if (teamElement == null) {
            parseError("could not read team number");
            return false;
        }
        team = teamElement.getAsInt();

        // cameras
        JsonElement camerasElement = obj.get("cameras");
        if (camerasElement == null) {
            parseError("could not read cameras");
            return false;
        }
        JsonArray cameras = camerasElement.getAsJsonArray();
        for (JsonElement camera : cameras) {
            if (!readCameraConfig(camera.getAsJsonObject())) {
                return false;
            }
        }
        return true;
    }

    public static VideoSource prepareCamera(CameraConfig config) {
        System.out.println("Starting camera '" + config.name + "' on " + config.path);
        UsbCamera camera = new UsbCamera(config.name, config.path);
        Gson gson = new GsonBuilder().create();

        camera.setFPS(30);
        //camera.setConfigJson(gson.toJson(config.config));
        camera.setResolution(1024, 576);
        //camera.setPixelFormat(PixelFormat.kMJPEG);
        camera.setConnectionStrategy(VideoSource.ConnectionStrategy.kKeepOpen);

        return camera;
    }

    // if(runProcessing) {

    // pipeline.process(mat); //process the mat (this does not change the mat, and
    // has an internal output to pipeline)
    // int contoursFound = pipeline.filterContoursOutput().size();
    // SmartDashboard.putString("More Vision State","Saw "+contoursFound+"
    // Contours");

    // if(contoursFound>=2) {

    // //get the contours from the vision algorithm
    // Rect rectOne = Imgproc.boundingRect(pipeline.filterContoursOutput().get(0));
    // Rect rectTwo = Imgproc.boundingRect(pipeline.filterContoursOutput().get(1));

    // if(contoursFound>2) {

    // Rect rectThree =
    // Imgproc.boundingRect(pipeline.filterContoursOutput().get(2)); //saw three+
    // contours, get the third contour

    // //initialize rectangle sorting ArrayList
    // ArrayList<Rect> orderedRectangles= new ArrayList<Rect>();
    // ContourAreaComparator areaComparator = new ContourAreaComparator();
    // orderedRectangles.add(rectOne);
    // orderedRectangles.add(rectTwo);
    // orderedRectangles.add(rectThree);

    // //sort the rectangles by area
    // Collections.sort(orderedRectangles, areaComparator);

    // //sort the smaller rectangles vertically
    // Rect topRect = (orderedRectangles.get(2).y>orderedRectangles.get(1).y) ?
    // orderedRectangles.get(1) : orderedRectangles.get(2);
    // Rect bottomRect = (orderedRectangles.get(2).y>orderedRectangles.get(1).y) ?
    // orderedRectangles.get(2) : orderedRectangles.get(1);

    // //repair the image using top and bottom rectangles
    // Rect mergedRect = new Rect((int) topRect.tl().x, (int) topRect.tl().y,
    // (int)(bottomRect.br().x-topRect.tl().x),
    // (int)(bottomRect.br().y-topRect.tl().y));

    // //recreate the rectangles from the repaired image
    // if(rectOne==orderedRectangles.get(1) || rectOne==orderedRectangles.get(2)){
    // rectOne = mergedRect;
    // rectTwo = orderedRectangles.get(0);
    // } else {
    // rectOne = orderedRectangles.get(0);
    // rectTwo = mergedRect;
    // }
    // }

    // //sort the rectangles horizontally
    // Rect rectLeft = (rectOne.x<rectTwo.x) ? rectOne : rectTwo;
    // Rect rectRight = (rectOne.x>rectTwo.x) ? rectOne : rectTwo;
    // rectOne = rectRight;
    // rectTwo = rectLeft;

    // //calculate center X and center Y pixels
    // centerXOne = rectOne.x + (rectOne.width/2); //returns the center of the
    // bounding rectangle
    // centerYOne = rectOne.y + (rectOne.height/2); //returns the center of the
    // bounding rectangle
    // centerXTwo = rectTwo.x + (rectTwo.width/2);
    // centerYTwo = rectTwo.y + (rectTwo.height/2);

    // double width=rectTwo.x-(rectOne.x+rectOne.width);
    // double height=rectOne.y-(rectTwo.y+rectTwo.height);

    // rectangleArea=width*height;
    // centerYAvg = (centerYOne + centerYTwo)/2;
    // centerXAvg = (centerXOne + centerXTwo)/2;

    // //draws the rectangles onto the camera image sent to the dashboard
    // Imgproc.rectangle(mat, new Point(rectOne.x, rectOne.y), new Point(rectTwo.x +
    // rectTwo.width, rectTwo.y + rectTwo.height), new Scalar(0, 0, 255), 2);
    // Imgproc.rectangle(mat, new Point(centerXAvg-3,centerYAvg-3), new
    // Point(centerXAvg+3,centerYAvg+3), new Scalar(255, 0, 0), 3);

    // SmartDashboard.putString("Vision State", "Executed overlay!");
    // }

    // SmartDashboard.putNumber("Center X", centerXAvg);
    // outputStream.putFrame(mat); //give stream (and CameraServer) a new frame
    // } else {
    // }

    // //Timer.delay(0.09);

    // public void disableProcessing() {
    // runProcessing = false;
    // }

    // public void enableProcessing() {
    // runProcessing = true;
    // }
    // /**
    // * Start running the camera.
    // */
    // public static VideoSource startCamera(CameraConfig config) {
    // System.out.println("Starting camera '" + config.name + "' on " +
    // config.path);
    // CameraServer inst = CameraServer.getInstance();
    // UsbCamera camera = new UsbCamera(config.name, config.path);
    // MjpegServer server = inst.startAutomaticCapture(camera);
    // Gson gson = new GsonBuilder().create();

    // camera.setConfigJson(gson.toJson(config.config));
    // camera.setConnectionStrategy(VideoSource.ConnectionStrategy.kKeepOpen);

    // if (config.streamConfig != null) {
    // server.setConfigJson(gson.toJson(config.streamConfig));
    // }

    // return camera;
    // }

}

// /**
// * Report parse error.
// */
// public static void parseError(String str) {
// System.err.println("config error in '" + configFile + "': " + str);
// }

// /**
// * Read single camera configuration.
// */
// public static boolean readCameraConfig(JsonObject config) {
// CameraConfig cam = new CameraConfig();

// // name
// JsonElement nameElement = config.get("name");
// if (nameElement == null) {
// parseError("could not read camera name");
// return false;
// }
// cam.name = nameElement.getAsString();

// // path
// JsonElement pathElement = config.get("path");
// if (pathElement == null) {
// parseError("camera '" + cam.name + "': could not read path");
// return false;
// }
// cam.path = pathElement.getAsString();

// // stream properties
// cam.streamConfig = config.get("stream");

// cam.config = config;

// cameraConfigs.add(cam);
// return true;
// }

// public static void setConfigFile(String inConfig) {
// configFile = inConfig;
// }
// /**
// * Read configuration file.
// */
// //@SuppressWarnings("PMD.CyclomaticComplexity")

// public static boolean readConfigFile() {
// // parse file
// JsonElement top;
// try {
// top = new JsonParser().parse(Files.newBufferedReader(Paths.get(configFile)));
// } catch (IOException ex) {
// System.err.println("could not open '" + configFile + "': " + ex);
// return false;
// }

// // top level must be an object
// if (!top.isJsonObject()) {
// parseError("must be JSON object");
// return false;
// }
// JsonObject obj = top.getAsJsonObject();

// // team number
// JsonElement teamElement = obj.get("team");
// if (teamElement == null) {
// parseError("could not read team number");
// return false;
// }
// team = teamElement.getAsInt();

// // ntmode (optional)
// if (obj.has("ntmode")) {
// String str = obj.get("ntmode").getAsString();
// if ("client".equalsIgnoreCase(str)) {
// server = false;
// } else if ("server".equalsIgnoreCase(str)) {
// server = true;
// } else {
// parseError("could not understand ntmode value '" + str + "'");
// }
// }

// // cameras
// JsonElement camerasElement = obj.get("cameras");
// if (camerasElement == null) {
// parseError("could not read cameras");
// return false;
// }
// JsonArray cameras = camerasElement.getAsJsonArray();
// for (JsonElement camera : cameras) {
// if (!readCameraConfig(camera.getAsJsonObject())) {
// return false;
// }
// }
// return true;
// }
