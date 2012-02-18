package com.shagami.android.sensorcheck;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class SensorCheckActivity extends Activity implements SensorEventListener {
	private SensorManager mSensorManagerAccel;
	private boolean mRegisteredSensorAccel;
	private SensorManager mSensorManagerMagnetic;
	private boolean mRegisteredSensorMagnetic;
	
	private float[] mOrient;
	
	private boolean mRunning = false;
	
	private View mView;
	private Canvas mCanvas;
	
	float[] mMagnetic = null;
	float[] mAccel = null;
	private boolean mSensorReady = false;
	float[] _R;
	float[] _I;
	float[] orientR;

	final static public int _MATRIX_SIZE = 16;
	final static public int _ORIENT_SIZE = 3;
	final static public int _XYZ_AXIS = 3;

	final private Handler mHandler = new Handler();

	final private Paint mPaintCircle = new Paint();
	final private Paint mPaintText = new Paint();
	final private Paint mPaintNorth = new Paint();
	final private Paint mPaintSouth = new Paint();

	private Drawable mBackground = null;

    private final Runnable mRunnable = new Runnable() {
        public void run() {
            update();
        }
    };
	
    TextView[] mTextOrient;
    TextView[] mTextMagnetic;
    TextView[] mTextAccel;
    
	private int mWidth;
	private int mHeight;
	private float mCenterX;
	private float mCenterY;
	private float mRadius;
	
	private Path mPathNorth;
	private Path mPathSouth;
	
    final public static float _TRIANGLE_STEP = 30.0f;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mTextOrient = new TextView[_XYZ_AXIS];
        mTextOrient[0] = (TextView) findViewById(R.id.textOrient);
        mTextOrient[1] = (TextView) findViewById(R.id.textOrientY);
        mTextOrient[2] = (TextView) findViewById(R.id.textOrientZ);

        mTextMagnetic = new TextView[_XYZ_AXIS];
        mTextMagnetic[0] =	(TextView) findViewById(R.id.textMagnetic);
        mTextMagnetic[1] =	(TextView) findViewById(R.id.textMagneticY);
        mTextMagnetic[2] =	(TextView) findViewById(R.id.textMagneticZ);

        mTextAccel = new TextView[_XYZ_AXIS];
        mTextAccel[0] =	(TextView) findViewById(R.id.textAccel);
        mTextAccel[1] =	(TextView) findViewById(R.id.textAccelY);
        mTextAccel[2] =	(TextView) findViewById(R.id.textAccelZ);
        
        mView = (View) findViewById(R.id.view1);
        
		mSensorManagerAccel = (SensorManager) getSystemService(SENSOR_SERVICE);
		mRegisteredSensorAccel = false;

		mSensorManagerMagnetic = (SensorManager) getSystemService(SENSOR_SERVICE);
		mRegisteredSensorMagnetic = false;
		
		mPathNorth = new Path();
		mPathSouth = new Path();
		
		mOrient = new float[_XYZ_AXIS];

		_R = new float[_MATRIX_SIZE];
		_I = new float[_MATRIX_SIZE];
		orientR = new float[_MATRIX_SIZE];
		
		mRunning = false;
		
    }
	
    Bitmap mLocalBitmap;
    BitmapDrawable mDrawable;
    
	@Override
	public void onWindowFocusChanged(boolean hasFocus){
		super.onWindowFocusChanged(hasFocus);
		
		drawPath(mView.getWidth(), mView.getHeight());
		
		mLocalBitmap = Bitmap.createBitmap(mView.getWidth(), mView.getHeight(), Bitmap.Config.ARGB_4444);
        mCanvas = new Canvas(mLocalBitmap);
        mCanvas.drawColor(Color.BLACK);
        
        mDrawable = new BitmapDrawable(mLocalBitmap);
        mDrawable.setBounds(0, 0, mView.getWidth(), mView.getHeight());      
        mDrawable.setAlpha(192);
        mDrawable.draw(mCanvas);
        mView.setBackgroundDrawable(mDrawable);
	}
    
	@Override
	public void onPause() {
		super.onPause();
		
		unregisterLocalListener();
        mHandler.removeCallbacks(mRunnable);
        
        mRunning = false;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		registerLocalListener();

		mRunning = true;
		update();
	}
	
    private void registerLocalListener() {
    	List<Sensor>  sensors = mSensorManagerAccel.getSensorList(Sensor.TYPE_ACCELEROMETER);
    	
    	if ( !mRegisteredSensorAccel && sensors.size() > 0 ) {
    		Sensor sensor = sensors.get(0);
    		mRegisteredSensorAccel = mSensorManagerAccel.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
    	}

    	sensors = mSensorManagerMagnetic.getSensorList(Sensor.TYPE_MAGNETIC_FIELD);
    	if ( !mRegisteredSensorMagnetic && sensors.size() > 0 ) {
    		Sensor sensor = sensors.get(0);
    		mRegisteredSensorMagnetic = mSensorManagerMagnetic.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
    	}
    }
    
    private void unregisterLocalListener() {
    	if ( mRegisteredSensorAccel ) {
    		mSensorManagerAccel.unregisterListener(this);
    		mRegisteredSensorAccel = false;
    	}
    	if ( mRegisteredSensorMagnetic ) {
    		mSensorManagerMagnetic.unregisterListener(this);
    		mRegisteredSensorMagnetic = false;
    	}
    }

	public void onSensorChanged(SensorEvent event) {
		/*
		 * 	http://developer.android.com/reference/android/hardware/SensorEvent.html#values
		 */
		switch ( event.sensor.getType() ) {
		case	Sensor.TYPE_ACCELEROMETER:
			mAccel = event.values.clone();
			break;
		case	Sensor.TYPE_MAGNETIC_FIELD:
			mSensorReady = true;
			mMagnetic = event.values.clone();
			break;
		}
		
		if ( mSensorReady && mMagnetic != null && mAccel != null ) {
			mSensorReady = false;
			
	        SensorManager.getRotationMatrix(_R, _I, mAccel, mMagnetic);
	        SensorManager.getOrientation(_R, mOrient);

	        if ( mOrient[0] < 0 ) {
	        	mOrient[0] = (float) (Math.toDegrees(mOrient[0]) + 360.0f);
	        } else {
	        	mOrient[0] = (float) (Math.toDegrees(mOrient[0]));
	        }
		}
	}

	private void update() {
        mHandler.removeCallbacks(mRunnable);
        
        if ( mTextOrient != null && mOrient != null ) {
        	for ( int i = 0; i < _XYZ_AXIS; ++i ) {
        		String lFormatString = String.valueOf(mOrient[i]);
        		mTextOrient[i].setText(lFormatString);
        	}
        }

       	if ( mTextMagnetic != null && mMagnetic != null && mMagnetic.length == _XYZ_AXIS ) {
        	for ( int i = 0; i < _XYZ_AXIS; ++i ) {
        		String lFormatString = String.valueOf(mMagnetic[i]);
        		mTextMagnetic[i].setText(lFormatString);
        	}
       	}

       	if ( mTextAccel != null && mAccel != null && mAccel.length == _XYZ_AXIS ) {
        	for ( int i = 0; i < _XYZ_AXIS; ++i ) {
        		String lFormatString = String.valueOf(mAccel[i]);
        		mTextAccel[i].setText(lFormatString);
        	}
        }

       	if ( mView != null && mCanvas != null && mDrawable != null ) {
       		drawCompass(mCanvas);
       		mDrawable.draw(mCanvas);
            mView.setBackgroundDrawable(mDrawable);
            mView.invalidate();
       	}

       	if ( mRunning ) {
        	mHandler.postDelayed(mRunnable, 1000);
        }
	}
	
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	private void drawPath ( int width, int height ) {
        mWidth = width;
        mHeight = height;
        
        mCenterX = width / 2;
        mCenterY = height / 2;
        
        if ( mWidth < mHeight ) {
        	mRadius = mWidth * 0.9f / 2.0f;
        } else {
        	mRadius = mHeight * 0.75f / 2.0f;
        }
        
        Paint paint = mPaintCircle;
        paint.setColor(Color.BLUE);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        paint = mPaintText;
        paint.setColor(Color.BLACK);
        paint.setAntiAlias(true);
        paint.setTextSize(28.0f);

        paint = mPaintNorth;
        paint.setColor(Color.RED);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
	
        paint = mPaintSouth;
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        
        mPathNorth.reset();
    	mPathNorth.moveTo(mCenterX - _TRIANGLE_STEP, mCenterY);
    	mPathNorth.lineTo(mCenterX, mCenterY - mRadius);
    	mPathNorth.lineTo(mCenterX + _TRIANGLE_STEP, mCenterY);
    	mPathNorth.lineTo(mCenterX - _TRIANGLE_STEP, mCenterY);
    	mPathNorth.close();
    	
    	mPathSouth.reset();
    	mPathSouth.moveTo(mCenterX - _TRIANGLE_STEP, mCenterY);
    	mPathSouth.lineTo(mCenterX, mCenterY + mRadius);
    	mPathSouth.lineTo(mCenterX + _TRIANGLE_STEP, mCenterY);
    	mPathSouth.lineTo(mCenterX - _TRIANGLE_STEP, mCenterY);
    	mPathSouth.close();
	}
	
    void drawCompass(Canvas canvas) {
    	Matrix mMatrix = new Matrix();
    	
    	canvas.drawColor(Color.BLACK);
    	canvas.drawCircle(mCenterX, mCenterY, mRadius, mPaintCircle);
    	
    	canvas.save();
    	mMatrix.setRotate(-mOrient[0], mCenterX, mCenterY);
    	canvas.setMatrix(mMatrix);
    	canvas.drawPath(mPathSouth,mPaintSouth);
    	canvas.drawPath(mPathNorth,mPaintNorth);
    	canvas.restore();
    }

}