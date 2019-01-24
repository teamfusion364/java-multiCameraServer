
/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

// import java.io.IOException;
// import java.nio.file.Files;
// import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

// import com.google.gson.Gson;
// import com.google.gson.GsonBuilder;
// import com.google.gson.JsonArray;
// import com.google.gson.JsonElement;
// import com.google.gson.JsonObject;
// import com.google.gson.JsonParser;

// import edu.wpi.cscore.MjpegServer;
// import edu.wpi.cscore.UsbCamera;
import edu.wpi.cscore.VideoSource;
// import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
// import edu.wpi.first.vision.VisionPipeline;
import edu.wpi.first.vision.VisionThread;

// import org.opencv.core.Mat;
import org.opencv.core.*;
import org.opencv.imgproc.*;

import team364_rpi.*;

public final class Main {

  private static DynamicVisionPipeline processingPipeline;

  public static double centerX;
  //public static double xValue_CenterX;

  private static Object imgLock = new Object();
  private static ArrayList<MatOfPoint> latestContours = new ArrayList<MatOfPoint>();

  private Main() { }

  /**
   * Main.
   */
  public static void main(String... args) {

    processingPipeline = new DynamicVisionPipeline();

    // setup and start NetworkTables
    NetworkTableInstance ntinst = NetworkTableInstance.getDefault();
    NetworkTable table = ntinst.getTable("visionParameters");

    NetworkTableEntry xValue = table.getEntry("xValue");
    NetworkTableEntry searchConfigNumber = table.getEntry("searchConfigNumber"); // 0: tape, 1: ball, 2: disk
    NetworkTableEntry visibleTargets = table.getEntry("visibleTargets");

    System.out.println("Setting up NetworkTables client for team " + 364);
    ntinst.startClientTeam(364);

    // Pass config file to the camera handler
    if (args.length > 0) {
      CameraStuff.setConfigFile(args[0]);
    }

    // read config file
    if (!CameraStuff.readConfigFile()) {
      return;
    }

    // start cameras
    List<VideoSource> cameras = new ArrayList<>();
    for (CameraStuff.CameraConfig cameraConfig : CameraStuff.cameraConfigs) {
      cameras.add(CameraStuff.startCamera(cameraConfig));
    }

    // start image processing on camera 0 if present
    if (cameras.size() >= 1) {
      VisionThread visionThread = new VisionThread(cameras.get(0), new DynamicVisionPipeline(), pipeline -> {
        if (!processingPipeline.filterContoursOutput().isEmpty()) {
          synchronized (imgLock) {
              // Setup pipeline to process TAPE, BALL, or DISK depending on NetworkTable input
              processingPipeline.setSearchConfigNumber((int)searchConfigNumber.getDouble(0));

              // Read out the latest output
              latestContours = processingPipeline.filterContoursOutput();
            }
          }
        });
      visionThread.start();
    }

    // loop forever and ever
    for (;;) {
      try {
        // Process a contour
        // Rect r;
        RotatedRect rotR;
        ArrayList<RotatedRect> visibleTargetsObserved = new ArrayList<RotatedRect>();

        // We have to LOCK to make sure 2nd thread doesn't change
        // latestContours while we're reading from it
        synchronized (imgLock) {
          //r = Imgproc.boundingRect(latestContours.get(0));
          
          // Iterate through output contours, bound by a rotated rectangle
          // and print out information about each rectangle
          for ( int i = 0; i < latestContours.size(); i++ ){
            MatOfPoint2f curContour2f = new MatOfPoint2f(latestContours.get(i));
            rotR = Imgproc.minAreaRect(curContour2f);
            visibleTargetsObserved.add(rotR);
            System.out.println(i + ": Angle: " + rotR.angle + " Center: " + rotR.center + " Size: " + rotR.size + "\n");
          }
        }
        //centerX = r.x + (r.width / 2);

        // Write to NetworkTable
        //xValue.setDouble(centerX);
        //visibleTargets.setNumberArray(visibleTargetsProcessed);

        // Rest (in milliseconds)
        Thread.sleep(100); // 0.1 seconds (10/sec)

      } catch (InterruptedException ex) {
        return;
      }
    }
  }
}
