package net.drjunior.ledstrip;

import ioio.lib.api.SpiMaster;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import java.io.IOException;
import java.util.Arrays;
import net.drjunior.ledstrip.R;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * An Android application that uses the IOIO board to connect to a RGB led
 * strip. The application has some cool effects and use the smartphone sensors
 * to change the "behavior" of the leds.
 * 
 * @author drjunior
 */
public class LedStripActivity extends IOIOActivity implements
		SensorEventListener {
	private static final String TAG = "HolidayIOIO";

	/** rainbow array colors **/
	private static final RGB[] rainbow = {
			new RGB((byte) 201, (byte) 31, (byte) 22), // 1
			new RGB((byte) 204, (byte) 54, (byte) 21),
			new RGB((byte) 217, (byte) 88, (byte) 14),
			new RGB((byte) 221, (byte) 104, (byte) 11),
			new RGB((byte) 230, (byte) 134, (byte) 1),
			new RGB((byte) 238, (byte) 173, (byte) 0),
			new RGB((byte) 252, (byte) 207, (byte) 3),
			new RGB((byte) 237, (byte) 228, (byte) 0),
			new RGB((byte) 206, (byte) 213, (byte) 0),
			new RGB((byte) 176, (byte) 197, (byte) 15), // 10
			new RGB((byte) 143, (byte) 187, (byte) 12),
			new RGB((byte) 119, (byte) 179, (byte) 18),
			new RGB((byte) 105, (byte) 176, (byte) 17),
			new RGB((byte) 71, (byte) 164, (byte) 30),
			new RGB((byte) 45, (byte) 156, (byte) 31),
			new RGB((byte) 24, (byte) 148, (byte) 37),
			new RGB((byte) 8, (byte) 131, (byte) 67),
			new RGB((byte) 14, (byte) 140, (byte) 98),
			new RGB((byte) 5, (byte) 148, (byte) 157),
			new RGB((byte) 8, (byte) 148, (byte) 182), // 20
			new RGB((byte) 31, (byte) 154, (byte) 215),
			new RGB((byte) 13, (byte) 132, (byte) 196),
			new RGB((byte) 3, (byte) 99, (byte) 163),
			new RGB((byte) 9, (byte) 74, (byte) 145),
			new RGB((byte) 17, (byte) 50, (byte) 121),
			new RGB((byte) 24, (byte) 35, (byte) 105),
			new RGB((byte) 29, (byte) 18, (byte) 89),
			new RGB((byte) 49, (byte) 12, (byte) 90),
			new RGB((byte) 91, (byte) 11, (byte) 90),
			new RGB((byte) 130, (byte) 0, (byte) 96), // 30
			new RGB((byte) 174, (byte) 9, (byte) 100),
			new RGB((byte) 197, (byte) 0, (byte) 89) // 32

	};

	/** number of leds in the strip **/
	private int LED_STRIP_LENGTH = 32;
	/** number of total bytes | 3 bytes(RGB) * 32 **/
	private int LEDS_BUFFER_LENGTH = LED_STRIP_LENGTH * 3;
	/** Half of the total bytes to send it using spi **/
	private int HALF_LEDS_BUFFER_LENGTH = LEDS_BUFFER_LENGTH / 2;

	/** buffers of bytes to store the values that will be sent to the led strip **/
	private byte[] buffer1 = new byte[HALF_LEDS_BUFFER_LENGTH];
	private byte[] buffer2 = new byte[HALF_LEDS_BUFFER_LENGTH];

	/** some rgb colors used in app **/
	private RGB RED = new RGB((byte) 255, (byte) 0, (byte) 0);
	private RGB GREEN = new RGB((byte) 0, (byte) 255, (byte) 0);
	private RGB BLUE = new RGB((byte) 0, (byte) 0, (byte) 255);
	private RGB BLACK = new RGB((byte) 0, (byte) 0, (byte) 0);
	private RGB WHITE = new RGB((byte) 255, (byte) 255, (byte) 255);

	/** buffer to store the 32 RGB colors that will be sent to the led strip **/
	private RGB[] ledsColor = new RGB[LED_STRIP_LENGTH];

	private SensorManager mSensorManager;
	private Sensor mLight;
	private Sensor mProximity;

	CheckdAudioFreq checkAudioFreq;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		Arrays.fill(ledsColor, BLACK);
		Arrays.fill(buffer1, (byte) 0x00);
		Arrays.fill(buffer2, (byte) 0x00);

		/** add 32 boxes to the layout **/
		LinearLayout containerLayout;
		containerLayout = (LinearLayout) findViewById(R.id.container);
		for (int i = 0; i < LED_STRIP_LENGTH; i++) {
			View box = LayoutInflater.from(this).inflate(R.layout.box, null);
			box.setId(i);
			containerLayout.addView(box);
			setLed(i, rainbow[i]);

		}

		/** instantiate the sensor manager and the sensors **/
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
		mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

		/**
		 * create and execute the asynctask that checks the sound amplitude
		 * constantly
		 **/
		checkAudioFreq = new CheckdAudioFreq();
		checkAudioFreq.execute();

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.menu_effects:
			showEffectsDialog();
			return true;
		case R.id.menu_sound_effect:
			setSoundEffect();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	boolean mUseRainbowEffect = true;
	boolean mUseProximitySensorEffect = false;
	boolean mUseLightSensorEffect = false;

	int positionEffectSelected = 0;

	boolean mUseSoundEffect = true;

	public void showEffectsDialog() {

		final CharSequence[] items = { "Rainbow", "Proximity Sensor",
				"Light Sensor" };

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Effects");

		builder.setSingleChoiceItems(items, positionEffectSelected,
				new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int item) {

						// reset effects
						setAllEffectsFalse();

						switch (item) {
						case 0:
							mUseRainbowEffect = true;
							break;
						case 1:
							mUseProximitySensorEffect = true;
							break;
						case 2:
							mUseLightSensorEffect = true;
							break;
						}

						positionEffectSelected = item;

						dialog.dismiss();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();

	}

	void setAllEffectsFalse() {

		mUseRainbowEffect = false;
		mUseProximitySensorEffect = false;
		mUseLightSensorEffect = false;
	}

	public void setSoundEffect() {

		if (mUseSoundEffect) {
			mUseSoundEffect = false;
			Toast.makeText(this, "Sound Effect OFF!", Toast.LENGTH_SHORT)
					.show();
		} else {
			mUseSoundEffect = true;
			Toast.makeText(this, "Sound Effect ON!", Toast.LENGTH_SHORT).show();
		}

	}

	@Override
	protected void onResume() {
		super.onResume();
		mSensorManager.registerListener((SensorEventListener) this, mLight,
				SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener((SensorEventListener) this, mProximity,
				SensorManager.SENSOR_DELAY_FASTEST);
	}

	@Override
	protected void onPause() {
		super.onPause();
		mSensorManager.unregisterListener(this);
		checkAudioFreq.stop();
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy) {

		Log.d(TAG, "Accuracy changed! " + sensor.getType());

	}

	public void onSensorChanged(SensorEvent event) {

		if (mUseLightSensorEffect) {
			if (event.sensor.getType() == Sensor.TYPE_LIGHT) {

				double attenuation = Math.min(1, event.values[0] / 1000.0);
				Log.d(TAG, "attenuation: " + attenuation);
				Log.d(TAG, "LUX value: " + event.values[0]);

				RGB newColor = new RGB((byte) 0, (byte) 0, (byte) 0);
				for (int i = 0; i < 32; i++) {

					newColor.copyValuesFrom(ledsColor[i]);
					newColor.r = attenuate(newColor.r, attenuation);
					newColor.g = attenuate(newColor.g, attenuation);
					newColor.b = attenuate(newColor.b, attenuation);
					setLed(i, newColor);
				}

			}
		} else if (mUseProximitySensorEffect) {
			if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
				Log.d(TAG, "PROXIMITY value: " + event.values[0]);
				if (event.values[0] == 0) {
					setAllLedsWithColor(BLACK);
				} else {
					setAllLedsWithColor(ledsColor[0]);
				}

			}
		}

	}

	private int mThreshold = 5;
	private boolean mBeatDetected = false;

	/**
	 * This AsyncTask is responsible for checking continuously the if the sound
	 * threshould is passed.
	 * 
	 * @author drjunior
	 * 
	 */
	private class CheckdAudioFreq extends AsyncTask<Void, double[], Void> {

		private boolean recordingEnabled = true;

		@Override
		protected Void doInBackground(Void... params) {

			int amp;
			SoundMeter soundMeter = new SoundMeter();

			try {
				soundMeter.start();
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			do {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				amp = (int) soundMeter.getAmplitude();
				// Log.i(TAG, "amplitude: " + amp);

				mBeatDetected = false;

				if (mUseSoundEffect) {
					if (amp > mThreshold) {
						mBeatDetected = true;
						setAllLedsWithColor(WHITE);
					}
				}

			} while (recordingEnabled);

			soundMeter.stop();
			return null;
		}

		/**
		 * Stops the CheckAudioFreq Asynctask
		 */
		public void stop() {
			recordingEnabled = false;
		}

	}

	/**
	 * Changes the color of all leds to one of the colors selected.
	 * 
	 * @param v
	 *            The view that has one of the possible colors.
	 */
	public void onClickColor(View v) {

		if (v.getId() == R.id.red) {
			setAllLedsWithColor(RED);
		} else if (v.getId() == R.id.green) {
			setAllLedsWithColor(GREEN);
		} else if (v.getId() == R.id.blue) {
			setAllLedsWithColor(BLUE);
		} else if (v.getId() == R.id.black) {
			setAllLedsWithColor(BLACK);
		} else if (v.getId() == R.id.white) {
			setAllLedsWithColor(WHITE);
		}

	}

	public void setAllLedsWithColor(RGB color) {
		Arrays.fill(ledsColor, color);
		for (int i = 0; i < LED_STRIP_LENGTH; i++) {
			setLed(i, color);
		}
	}

	/**
	 * Changes the color of the led in position v.getId();
	 * 
	 * @param v
	 *            The view that the user clicks
	 */
	public void onClick(View v) {

		int position = v.getId();
		position = 31 - position;

		if (ledsColor[position] == RED) {
			ledsColor[position] = GREEN;
			setLed(position, GREEN);
			v.findViewById(R.id.box).setBackgroundColor(Color.GREEN);
		} else if (ledsColor[position] == GREEN) {
			ledsColor[position] = BLUE;
			setLed(position, BLUE);
			v.findViewById(R.id.box).setBackgroundColor(Color.BLUE);
		} else if (ledsColor[position] == BLUE) {
			ledsColor[position] = BLACK;
			setLed(position, BLACK);
			v.findViewById(R.id.box).setBackgroundColor(Color.BLACK);
		} else if (ledsColor[position] == BLACK) {
			ledsColor[position] = RED;
			setLed(position, RED);
			v.findViewById(R.id.box).setBackgroundColor(Color.RED);
		} else {
			ledsColor[position] = RED;
			setLed(position, RED);
			v.findViewById(R.id.box).setBackgroundColor(Color.RED);
		}
	}

	/** Attenuates a brightness level. */
	private byte attenuate(byte color, double attenuation) {
		double d = (double) ((int) color & 0xFF) / 256;
		d *= attenuation;
		return (byte) (d * 256);
	}

	/**
	 * Set the color of the led in the position indicated
	 * 
	 * @param position
	 * @param color
	 */
	private void setLed(int position, RGB color) {

		synchronized (LedStripActivity.this) {
			// check if position fits in the first buffer
			if ((position * 3) < HALF_LEDS_BUFFER_LENGTH) {
				position = position * 3;
				buffer1[position] = color.r;
				buffer1[position + 1] = color.g;
				buffer1[position + 2] = color.b;
			} else {
				position = position - LED_STRIP_LENGTH / 2;
				position = position * 3;
				buffer2[position] = color.r;
				buffer2[position + 1] = color.g;
				buffer2[position + 2] = color.b;
			}
		}

	}

	/** An RGB triplet. */
	private static class RGB {
		byte r;
		byte g;
		byte b;

		RGB(byte r, byte g, byte b) {
			this.r = r;
			this.g = g;
			this.b = b;
		}

		public void copyValuesFrom(RGB from) {
			this.r = from.r;
			this.g = from.g;
			this.b = from.b;
		}
	}

	class IOIOThread extends BaseIOIOLooper {
		private SpiMaster spi_;

		@Override
		protected void setup() throws ConnectionLostException {
			spi_ = ioio_.openSpiMaster(5, 4, 3, 6, SpiMaster.Rate.RATE_50K);

		}

		@Override
		public void loop() throws ConnectionLostException {

			try {

				manageLedsBehavior();

				sendBuffersOverSpi();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}

		/**
		 * Function responsible for managing the possible leds effects. In this
		 * case it is only controlling the rainbow spinning effect, but it
		 * should also be controlling the light sensor effect and the sound
		 * "sensor".
		 * <p>
		 * FIXME Put the controlling of light sensor and sound sensor inside
		 * this function.
		 * 
		 * @throws InterruptedException
		 */

		void manageLedsBehavior() throws InterruptedException {

			// this sleep is really important because otherwise the led
			// strip starts blinking
			Thread.sleep(30);

			if (mUseRainbowEffect) {
				if (!mBeatDetected) {
					spinRainbow();
				}
			}
		}

		private int index = 0;

		/**
		 * Function that does the rainbow spinning effect. It basically moves
		 * the position of the colors in the buffer.
		 */
		private void spinRainbow() {

			index++; // increment index
			if (index == LED_STRIP_LENGTH)
				index = 0; // reset index
			for (int i = 0; i < LED_STRIP_LENGTH; i++) {
				setLed(i, rainbow[(index + i) % 31]);
			}
		}

		/**
		 * Sends the buffer1 and the buffer2 over the spi protocol
		 * 
		 * @throws ConnectionLostException
		 * @throws InterruptedException
		 */
		private void sendBuffersOverSpi() throws ConnectionLostException,
				InterruptedException {

			// Since SPI messages are limited to 64 bytes, and we need to send
			// 96 bytes, we divide the message into two chunks of 48. We assume
			// that the SPI clock is slow enough (50K) so that the second half
			// will finish being sent to the IOIO before the first half
			// finished transmission.
			synchronized (LedStripActivity.this) {
				spi_.writeReadAsync(0, buffer1, buffer1.length, buffer1.length,
						null, 0);
				spi_.writeRead(buffer2, buffer2.length, buffer2.length, null, 0);
			}
		}

	}

	@Override
	protected IOIOLooper createIOIOLooper() {
		return new IOIOThread();
	}

}