package cz.zveleba.zipapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;
import org.json.JSONArray;

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
//import android.os.StrictMode;
import android.graphics.Color;

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
//import android.widget.LinearLayout;
//import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.ToggleButton;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

//import cz.zveleba.zipapp.R;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
//import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
//import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;


public class Main extends Activity implements OnItemSelectedListener {

    private Spinner spin_devices;
    private JSONArray devicesArray;
    private int selectedDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        spin_devices = (Spinner) findViewById(R.id.devices);
        spin_devices.setOnItemSelectedListener(this);

        // Toggle state switch / on/off
        ToggleButton btn_state = (ToggleButton) findViewById(R.id.state);
        btn_state.setOnCheckedChangeListener(new ListenToggle());

        // Device list reload button
        ImageButton btn_reload = (ImageButton) findViewById(R.id.btnReload);
        btn_reload.setOnClickListener(new ListenReload());

        // Name set button
        Button but_name = (Button) findViewById(R.id.set_name);
        but_name.setOnClickListener(new ListenSetName());

        // Light
        // White balance set button
        Button but_wbal = (Button) findViewById(R.id.set_wbal);
        but_wbal.setOnClickListener(new ListenSetWbal());
        // Brightness override
        SeekBar bar_bo = (SeekBar) findViewById(R.id.bar_bo);
        bar_bo.setOnSeekBarChangeListener(new ListenBarBO());
        // Hue / color
        SeekBar bar_hue = (SeekBar) findViewById(R.id.bar_hue);
        bar_hue.setOnSeekBarChangeListener(new ListenBarHue());
        // Saturation
        SeekBar bar_sat = (SeekBar) findViewById(R.id.bar_sat);
        bar_sat.setOnSeekBarChangeListener(new ListenBarSat());
        // Value / brightness
        SeekBar bar_val = (SeekBar) findViewById(R.id.bar_val);
        bar_val.setOnSeekBarChangeListener(new ListenBarVal());

        // Discover devices
        discover();
    }

    void discover() {
        // get info textview
        TextView txt = (TextView) findViewById(R.id.infoView);
        txt.setVisibility(View.VISIBLE);

        // check if wifi is enabled
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (! wifi.isWifiEnabled()) {
            txt.setText(R.string.wifi_disabled);
        }
        else {
            txt.setText(R.string.searching);
            (new SsdpDiscovery()).execute();
        }
    }

    public void newDevices() {
        // debug
        //TextView txt = (TextView) findViewById(R.id.textView);
        //txt.setText(devicesArray.toString());
        try {
            // Init devices spinner
            List<String> listDevices = new ArrayList<String>();

            // for each device - put device name to list
            for (int x=0; x < devicesArray.length(); x++) {
                JSONObject device = devicesArray.getJSONObject(x);
                listDevices.add(device.getString("name"));
            }

            // add the list to devices spinner
            ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, listDevices);
            dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spin_devices.setAdapter(dataAdapter);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // Hide info view
        TextView txt = (TextView) findViewById(R.id.infoView);
        txt.setText("");
        txt.setVisibility(View.GONE);

        // Hide previous content
        findViewById(R.id.light_layout).setVisibility(View.GONE);
        selectedDevice = pos;
        try {
            JSONObject device = devicesArray.getJSONObject(pos);
            // START device is light
            if (device.getString("class").equals("light")) {
                findViewById(R.id.light_layout).setVisibility(View.VISIBLE);

                CompoundButton state = (CompoundButton) findViewById(R.id.state);

                if (device.getString("state").equals("1")) {
                    state.setChecked(true);
                }
                else {
                    state.setChecked(false);
                }

                // Set name in edit textbox
                ((EditText) findViewById(R.id.edit_name)).setText(device.getString("name"), TextView.BufferType.EDITABLE);

                // Set white balance in edit textbox
                if (device.has("wb")) {
                    ((EditText) findViewById(R.id.edit_wbal)).setText(device.getString("wb"), TextView.BufferType.EDITABLE);
                }

                if (device.has("bri")) {
                    ((SeekBar) findViewById(R.id.bar_bo)).setProgress(device.getInt("bri"));
                }

                if (device.has("color")) {
                    Float h = (float)device.getDouble("h");
                    Float s = (float)device.getDouble("s");
                    Float v = (float)device.getDouble("v");
                    ((SeekBar) findViewById(R.id.bar_hue)).setProgress(Math.round(h));
                    ((SeekBar) findViewById(R.id.bar_sat)).setProgress(Math.round(s*1000));
                    ((SeekBar) findViewById(R.id.bar_val)).setProgress(Math.round(v*1000));
                    float[] hsv = {h,s,v};
                    findViewById(R.id.color_view).setBackgroundColor(Color.HSVToColor(hsv));
                }
            }
            // END light

        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    //--------------------------------------------------------------------------------------------//
    //  HTTP communication                                                                        //
    //--------------------------------------------------------------------------------------------//

    private String httpGet(String url) {
        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(url);
        HttpResponse response;
        try {
            response = client.execute(request);

            BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            StringBuffer sb = new StringBuffer("");
            String line;
            while ((line = in.readLine()) != null){
                sb.append(line + "\n");
            }
            in.close();
            return sb.toString();
        }
        catch (Exception e) {
            e.printStackTrace();
            return "Error";
        }
    }

    private class HttpSet extends AsyncTask<List<NameValuePair>, Void, List<NameValuePair>> {
        @Override
        protected List<NameValuePair> doInBackground(List<NameValuePair>... data) {
            try {
                HttpClient httpClient = new DefaultHttpClient();
                HttpPost httpPost = new HttpPost("http://" + devicesArray.getJSONObject(selectedDevice).getString("ip"));

                httpPost.setEntity(new UrlEncodedFormEntity(data[0]));
                httpClient.execute(httpPost);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return data[0];
        }
    }

    //--------------------------------------------------------------------------------------------//
    //  Device discovery                                                                          //
    //--------------------------------------------------------------------------------------------//

    private class SsdpDiscovery extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void...args) {

            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

            String json = "{}";
            if (wifi != null) {
                WifiManager.MulticastLock lock = wifi.createMulticastLock("The Lock");
                lock.acquire();
                DatagramSocket socket = null;
                try {
                    // Send search packet
                    InetAddress group = InetAddress.getByName("239.255.255.250");
                    int port = 1900;
                    String query =
                            "M-SEARCH * HTTP/1.1\r\n" +
                                    "HOST: 239.255.255.250:1900\r\n" +
                                    "MAN: \"ssdp:discover\"\r\n" +
                                    "MX: 1\r\n" +
                                    "ST: upnp:rootdevice\r\n" +
                                    "\r\n";

                    socket = new DatagramSocket(port);
                    socket.setReuseAddress(true);

                    DatagramPacket dgram = new DatagramPacket(query.getBytes(), query.length(), group, port);
                    socket.send(dgram);

                    // Let's consider all the responses we can get in 1 second
                    long time = System.currentTimeMillis();
                    long curTime = System.currentTimeMillis();
                    json = "{'devices': [";
                    while (curTime - time < 1000) {
                        DatagramPacket p = new DatagramPacket(new byte[1024], 1024);
                        socket.setSoTimeout(100);
                        try {
                            socket.receive(p);
                            String s = new String(p.getData());
                            if (s.startsWith("HTTP/1.1 200")) {
                                // Light found
                                if (s.contains("ZIP-Light")) {
                                    // Json formating
                                    if (!json.equals("{'devices': [")) {
                                        json += ",\n";
                                    }
                                    // Add light to json
                                    json += "{'class': 'light', 'ip': '" + p.getAddress().getHostAddress() + "'}";
                                }
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                        curTime = System.currentTimeMillis();
                    }
                    json += "]}";

                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    socket.close();
                }
                lock.release();

                // Get status of devices
                try {
                    JSONObject devicesJson = new JSONObject(json);
                    devicesArray = devicesJson.getJSONArray("devices");

                    // for each device
                    for (int x=0; x < devicesArray.length(); x++) {
                        JSONObject device = devicesArray.getJSONObject(x);
                        // process line by line
                        Scanner scanner = new Scanner(httpGet("http://" + device.getString("ip") + "/status"));
                        while (scanner.hasNextLine()) {
                            String line = scanner.nextLine();
                            if (line.startsWith("NAME:")) { device.put("name", line.split(":")[1]); }
                            if (line.startsWith("STATE:")) { device.put("state", line.split(":")[1]); }
                            if (line.startsWith("BRIGHTNESS:")) { device.put("bri", line.split(":")[1]); }
                            if (line.startsWith("COLOR:")) {
                                try {
                                    int r = Integer.parseInt(line.split(":")[1].split(",")[0]);
                                    int g = Integer.parseInt(line.split(":")[1].split(",")[1]);
                                    int b = Integer.parseInt(line.split(":")[1].split(",")[2]);
                                    float[] hsv = new float[3];
                                    Color.RGBToHSV(r, g, b, hsv);
                                    device.put("h", hsv[0]);
                                    device.put("s", hsv[1]);
                                    device.put("v", hsv[2]);
                                }
                                finally {device.put("color", true);}
                            }
                        }
                        scanner.close();
                        devicesArray.put(x, device);
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return json;
        }

        @Override
        protected void onPostExecute(String json) {
            newDevices();
        }
    }

    //--------------------------------------------------------------------------------------------//
    //  Listeners                                                                                 //
    //--------------------------------------------------------------------------------------------//

    // Toggle on/off
    private class ListenToggle implements CompoundButton.OnCheckedChangeListener {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            List<NameValuePair> data = new ArrayList<NameValuePair>();
            if (isChecked) {
                data.add(new BasicNameValuePair("api", "1"));
                data.add(new BasicNameValuePair("set", "1"));
                data.add(new BasicNameValuePair("on", "1"));
            } else {
                data.add(new BasicNameValuePair("api", "1"));
                data.add(new BasicNameValuePair("set", "1"));
            }

            try {
                HttpSet httpSet = new HttpSet();
                httpSet.execute(data);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Discover new devices
    private class ListenReload implements OnClickListener{
        @Override
        public void onClick(View v) {
            discover();
        }
    }

    // Set device name
    private class ListenSetName implements OnClickListener{
        @Override
        public void onClick(View v) {
            String new_name = ((EditText) findViewById(R.id.edit_name)).getText().toString();
            if (new_name.length() > 3) {

                List<NameValuePair> data = new ArrayList<NameValuePair>();
                data.add(new BasicNameValuePair("api", "1"));
                data.add(new BasicNameValuePair("name", new_name));

                try {
                    HttpSet httpSet = new HttpSet();
                    httpSet.execute(data);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Set white balance
    private class ListenSetWbal implements OnClickListener{
        @Override
        public void onClick(View v) {
            String new_wb = ((EditText) findViewById(R.id.edit_wbal)).getText().toString();
            if (new_wb.length() >= 5 && new_wb.split(",").length == 3) {
                List<NameValuePair> data = new ArrayList<NameValuePair>();
                data.add(new BasicNameValuePair("api", "1"));
                data.add(new BasicNameValuePair("wb", new_wb));

                try {
                    HttpSet httpSet = new HttpSet();
                    httpSet.execute(data);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Set brightness override
    private class ListenBarBO implements OnSeekBarChangeListener {
        private String progress;

        @Override
        public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
            progress = ((Integer) progresValue).toString();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            List<NameValuePair> data = new ArrayList<NameValuePair>();
            data.add(new BasicNameValuePair("api", "1"));
            data.add(new BasicNameValuePair("brightness", progress));

            try {
                HttpSet httpSet = new HttpSet();
                httpSet.execute(data);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Set hue
    private class ListenBarHue implements OnSeekBarChangeListener {
        Float h;
        Float s;
        Float v;

        @Override
        public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
            try {
                JSONObject device = devicesArray.getJSONObject(selectedDevice);
                h = (float)progresValue;
                s = (float) device.getDouble("s");
                v = (float) device.getDouble("v");
                float[] hsv = {h, s, v};
                findViewById(R.id.color_view).setBackgroundColor(Color.HSVToColor(hsv));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            try {
                JSONObject device = devicesArray.getJSONObject(selectedDevice);
                device.put("h", h);
                device.put("s", s);
                device.put("v", v);
                float[] hsv = {h, s, v};
                int color = Color.HSVToColor(hsv);

                List<NameValuePair> data = new ArrayList<NameValuePair>();
                data.add(new BasicNameValuePair("api", "1"));
                data.add(new BasicNameValuePair("color", Color.red(color) + "," + Color.green(color) + "," + Color.blue(color)));

                Main.HttpSet httpSet = new HttpSet();
                httpSet.execute(data);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Set saturation
    private class ListenBarSat implements OnSeekBarChangeListener {
        Float h;
        Float s;
        Float v;

        @Override
        public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
            try {
                JSONObject device = devicesArray.getJSONObject(selectedDevice);
                h = (float) device.getDouble("h");
                s = (float)progresValue/1000;
                v = (float) device.getDouble("v");
                float[] hsv = {h, s, v};
                findViewById(R.id.color_view).setBackgroundColor(Color.HSVToColor(hsv));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            try {
                JSONObject device = devicesArray.getJSONObject(selectedDevice);
                device.put("h", h);
                device.put("s", s);
                device.put("v", v);
                float[] hsv = {h, s, v};
                int color = Color.HSVToColor(hsv);

                List<NameValuePair> data = new ArrayList<NameValuePair>();
                data.add(new BasicNameValuePair("api", "1"));
                data.add(new BasicNameValuePair("color", Color.red(color) + "," + Color.green(color) + "," + Color.blue(color)));

                Main.HttpSet httpSet = new HttpSet();
                httpSet.execute(data);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Set brightness
    private class ListenBarVal implements OnSeekBarChangeListener {
        Float h;
        Float s;
        Float v;

        @Override
        public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
            try {
                JSONObject device = devicesArray.getJSONObject(selectedDevice);
                h = (float) device.getDouble("h");
                s = (float) device.getDouble("s");
                v = (float)progresValue/1000;
                float[] hsv = {h, s, v};
                findViewById(R.id.color_view).setBackgroundColor(Color.HSVToColor(hsv));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            try {
                JSONObject device = devicesArray.getJSONObject(selectedDevice);
                device.put("h", h);
                device.put("s", s);
                device.put("v", v);
                float[] hsv = {h, s, v};
                int color = Color.HSVToColor(hsv);

                List<NameValuePair> data = new ArrayList<NameValuePair>();
                data.add(new BasicNameValuePair("api", "1"));
                data.add(new BasicNameValuePair("color", Color.red(color) + "," + Color.green(color) + "," + Color.blue(color)));

                Main.HttpSet httpSet = new HttpSet();
                httpSet.execute(data);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}