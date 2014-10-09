package features;

import java.util.ArrayList;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.KeyPoint;
import org.opencv.video.Video;

import dlsu.vins.R;
import ekf.PointDouble;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;

public class FeatureManager implements CvCameraViewListener2 {
    private static final String TAG = "Feature Manager";
    private FeatureDetector detector;
    private final Scalar RED = new Scalar(255,0,0);
    private final Scalar BLACK = new Scalar(0);
    private final Scalar WHITE = new Scalar(255);
    
    
    // TODO: Change this to either VideoCapture or another alternative
    private CameraBridgeViewBase mOpenCvCameraView;
    
    private BaseLoaderCallback mLoaderCallback;

    
    public FeatureManager(Activity caller) {
        Log.i(TAG, "constructed");
        
        Log.i(TAG, "Trying to load OpenCV library");
        loadOpenCv(caller);
        
        mOpenCvCameraView = (CameraBridgeViewBase) caller.findViewById(R.id.surface_view);
        // http://stackoverflow.com/a/17872107
        //mOpenCvCameraView.setMaxFrameSize(720, 1280); // sets to 720 x 480
        mOpenCvCameraView.setMaxFrameSize(400, 1280); // sets to 320 x 240
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        
        mOpenCvCameraView.setCvCameraViewListener(this);
        
        if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, caller, mLoaderCallback)) {
          Log.e(TAG, "Cannot connect to OpenCV Manager");
        }
    }
    
    private void loadOpenCv(Activity caller) {
    	mLoaderCallback = new BaseLoaderCallback(caller) {
            @Override
            public void onManagerConnected(int status) {
                switch (status) {
                    case LoaderCallbackInterface.SUCCESS: {
                        Log.i(TAG, "OpenCV loaded successfully");
                        mOpenCvCameraView.enableView();
                        prevCurrent = new MatOfPoint2f();
                        prevImage = new Mat();
                        detector = FeatureDetector.create(FeatureDetector.FAST);
                    } break;
                    default: {
                        super.onManagerConnected(status);
                    } break;
                }
            }
        };
    }
    
        
    private Size imageSize;
    private Mat cameraMatrix, distCoeffs, Rot, T;
    private Mat R1,R2,P1,P2,Q;
    private Mat points4D;

    public void onCameraViewStarted(int width, int height) {
    	 
        // INITIALIZATION FOR STEREORECTIFY
        
        // INPUT VARIABLES
        
        cameraMatrix = Mat.zeros(3, 3, CvType.CV_64F);
        distCoeffs =  Mat.zeros(5, 1, CvType.CV_64F);
        imageSize = new Size(1920, 1080);
        Rot = Mat.zeros(3, 3, CvType.CV_64F);
        T = Mat.ones(3, 1, CvType.CV_64F);
        
        cameraMatrix.put(0, 0, 1768.104971372035, 0, 959.5);
        cameraMatrix.put(1, 0, 0, 1768.104971372035, 539.5);
        cameraMatrix.put(2, 0, 0, 0, 1);
        
        distCoeffs.put(0, 0, 0.1880897270445046);
        distCoeffs.put(1, 0, -0.7348187497379466);
        distCoeffs.put(2, 0, 0);
        distCoeffs.put(3, 0, 0);
        distCoeffs.put(4, 0, 0.6936210153459164);
        
        Rot.put(0, 0, 1, 0, 0);
        Rot.put(1, 0, 0, 1, 0);
        Rot.put(2, 0, 0, 0, 1);
        
        // OUTPUT VARIABLES
        
        R1 = Mat.zeros(3, 3, CvType.CV_64F);
        R2 = Mat.zeros(3, 3, CvType.CV_64F);
        P1 = Mat.zeros(3, 4, CvType.CV_64F);
        P2 = Mat.zeros(3, 4, CvType.CV_64F);
        Q = Mat.zeros(4, 4, CvType.CV_64F);
        
        // INITIALIZATION END
        
        // CALL STEREORECTIFY EACH FRAME AFTER THE FIRST
        // JUST PASS A NEW ROTATION AND TRANSLATION MATRIX
        
        Calib3d.stereoRectify(cameraMatrix, distCoeffs, cameraMatrix.clone(), distCoeffs.clone(), imageSize, Rot, T, R1, R2, P1, P2, Q);
    }

    public void onCameraViewStopped() {}
    
    
    
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Log.d("VINS", "onCameraFrame");
        currentImage= inputFrame.gray();
        frames++;
        return currentImage;
    }
    
    private MatOfPoint2f convert(MatOfKeyPoint keyPoints) {
        KeyPoint[] keyPointsArray = keyPoints.toArray();
        Point[] pointsArray = new Point[keyPointsArray.length];
        
        for (int i = 0; i < keyPointsArray.length; i++) {
            pointsArray[i] = (Point) keyPointsArray[i].pt;
        }
        
        return new MatOfPoint2f(pointsArray);
    }
        
    private MatOfPoint2f prevCurrent;
    private MatOfPoint2f prevNew;
    
    private Mat prevImage;
    private Mat currentImage;
    
    private int frames = 0;
    
    
    public FeatureUpdate getFeatureUpdate() {
    	Log.d(TAG, "Getting Feature Update");
    	
    	Mat detectMask = currentImage.clone();
        detectMask.setTo(WHITE);
        
        FeatureUpdate update = new FeatureUpdate();
        
        if (prevCurrent.size().height > 0) {
            
        	//// Optical Flow
        	
        	MatOfByte status = new MatOfByte();
            MatOfFloat err = new MatOfFloat();
            MatOfPoint2f nextFeatures = new MatOfPoint2f();
            
            int prevCurrentSize = (int) prevCurrent.size().height; // whut
            if (prevNew != null && prevNew.size().height > 0)
            	prevCurrent.push_back(prevNew); // combined
            
            Video.calcOpticalFlowPyrLK(prevImage, currentImage, prevCurrent, nextFeatures, status, err);
            
            List<Point> oldPoints = prevCurrent.toList();
            List<Point> newPoints = nextFeatures.toList();
            List<Point> goodOldList = new ArrayList<>();
            List<Point> goodNewList = new ArrayList<>();
            List<Integer> badPointsIndex = new ArrayList<>();
            
            // Use status to split good from bad  
            
            int index = 0;
            int currentSize = 0;
            for (Byte item : status.toList()) {
                if (item.intValue() == 1) {
                	if (index < prevCurrentSize)
                		currentSize++;
                	goodOldList.add(oldPoints.get(index));
                    goodNewList.add(newPoints.get(index));
                	Core.circle(detectMask, newPoints.get(index), 10, BLACK, -1); // mask out during detection         
                }
                else {
                    badPointsIndex.add(Integer.valueOf(index));
                }
                index++;
            }
            
            // Convert from List to OpenCV matrix for triangulation
            MatOfPoint2f goodOld = new MatOfPoint2f();
            MatOfPoint2f goodNew = new MatOfPoint2f();
            
            goodOld.fromList(goodOldList);
            goodNew.fromList(goodNewList);
            
            
            //// TRIANGULATION ???
            
            // TODO: might want to initialize points4D with a large Nx4 Array
            //		 so that both memory and time will be saved (instead of reallocation each time)
            //		 consider converting to Euclidean, but maybe no need.
            
            
            if(!goodOld.empty() && !goodNew.empty()) {
            	points4D = Mat.zeros(1, 4, CvType.CV_64F);
            	Calib3d.triangulatePoints(P1, P2, goodOld, goodNew, points4D);
            	
            	//Mat points3D = new Mat();
            	//Calib3d.convertPointsFromHomogeneous(points4D, points3D);
            	
            	// TODO verify this shit
            	// Split points to current and new PointDouble
                List<PointDouble> current2d = new ArrayList<>();
                List<PointDouble> new2d = new ArrayList<>();
                for (int i = 0; i < goodOld.height(); i++) {
                	double x = points4D.get(0, i)[0];
                	double y = points4D.get(1, i)[0];
                	
                	PointDouble point = new PointDouble(x, y);
                	if (i < currentSize) {
                		current2d.add(point);
                	}
                	else {
                		new2d.add(point);
                	}
                }
                
                update.currentPoints = current2d;
                update.newPoints = new2d;
            }
            update.badPointsIndex = badPointsIndex;
            goodNew.copyTo(prevCurrent);
        }
        
        
        // Detect new points based on optical flow mask
        MatOfKeyPoint newFeatures = new MatOfKeyPoint();
        detector.detect(currentImage, newFeatures, detectMask);
        if (newFeatures.size().height > 0) {
            prevNew = convert(newFeatures);
        }
    
        currentImage.copyTo(prevImage);
        return update;
    }
}


