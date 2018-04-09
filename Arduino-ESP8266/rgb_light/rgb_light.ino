#include <ESP8266WiFi.h>
#include <WiFiUdp.h>
#include <ESP8266WebServer.h>
#include <ArduinoJson.h>
#include <stdio.h>
#include "FS.h"

String split(String data, char separator, int index);
void loadConf();
void saveConf();
void setColor(int tr, int tg, int tb);

/*----------------------*/
/*       Config         */
/* ---------------------*/
// Networking
char ssid[] = "SSID";
char pass[] = "password";

unsigned int discovery_port = 1900;
IPAddress ipMulti(239, 255, 255, 250);
IPAddress ip;
ESP8266WebServer server(80);
WiFiUDP udp;

// Hardware
int ledRed = 12;
int ledGreen = 14;
int ledBlue = 13;

// Default color
int r = 128;
int g = 128;
int b = 128;
// Whitebalace
float wbr = 1;
float wbg = 1;
float wbb = 1;

bool onState = 0;
String name = "light";
int brightness = 100;

/*----------------------*/
/*        SETUP         */
/* ---------------------*/

void setup() {
  Serial.begin(115200);
  Serial.println();
  Serial.println("Starting HW init...");
  analogWriteRange(255);

  if (!SPIFFS.begin()) {
    Serial.println("Failed to mount file system");
  }
  delay(100);
  loadConf();
  
  pinMode(ledRed, OUTPUT);
  pinMode(ledGreen, OUTPUT);
  pinMode(ledBlue, OUTPUT);

  Serial.println("Starting WiFi...");
  
  // Connect to wifi and wait for connection
  WiFi.begin(ssid, pass); 
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
  }
  ip = WiFi.localIP();

  Serial.println("WiFi connected");
  Serial.print("IP address: ");
  Serial.println(ip);
  Serial.println("Starting UDP listen...");
  
  // Start UDP
  udp.beginMulticast(WiFi.localIP(), ipMulti, discovery_port);
  
  Serial.println("Starting web server...");
  
  // Start the server
  server.on("/", pageIndex);
  server.on("/status", pageStatus);
  server.onNotFound(page404);
  server.begin();
}

/*----------------------*/
/*        LOOP          */
/* ---------------------*/

void loop() {
  // UPNP Discovery - vury cut down version
  int packetSize = udp.parsePacket();
  if (packetSize) {
    // read the packet into packetBufffer
    char temp[20];
    udp.read(temp, 20);
    String packetBuffer = temp;

    if (packetBuffer.indexOf("M-SEARCH * HTTP/1.1") != -1) {
      static const char responseTemplate[] = "HTTP/1.1 200 OK\n"
        "EXT:\n"
        "LOCATION: http://%u.%u.%u.%u:80/\n" // WiFi.localIP()
        "SERVER: Arduino/ESP8266, UPnP/1.0, ZIP-Light/0.1\n"
        "ST:upnp:rootdevice\n"
        "USN: uuid:22b2bd50-00e3-11e7-93ae-92361f002671::upnp:rootdevice\n"
        "\n";
      
      char buffer[1460];
      snprintf(buffer, sizeof(buffer), responseTemplate, ip[0], ip[1], ip[2], ip[3]);
      
      // Send UPNP response
      udp.beginPacket(udp.remoteIP(), discovery_port);
      udp.write(buffer);
      udp.endPacket();
    }
  }
  // Web server
  server.handleClient();
}

/*----------------------*/
/*      WEB PAGES       */
/* ---------------------*/

void pageIndex() {
  bool save = 0;
  // Get new name from POST
  if (server.hasArg("name")) {
    String temp = server.arg("name");
    if (temp != name) {
      name = temp;
      save = 1;
    }
  }
  
  // Get ON state from POST
  if (server.hasArg("set")) {
    if (server.hasArg("on")) {
      if (!onState) {
        setColor(r, g, b);
        onState = 1;
      }
    }
    else {
      if (onState) {
        setColor(0, 0, 0);
        onState = 0;
      }
    }
  }

  // Get brightness from POST
  if (server.hasArg("brightness")) {
    brightness = server.arg("brightness").toInt();
    setColor(r, g, b);
  }

  // Get white balance from POST
  if (server.hasArg("wb")) {
    String wb = server.arg("wb");
    float twb = split(wb, 0).toFloat();
    if (wbr != twb) {
      wbr = twb;
      save = 1;
    }
    twb = split(wb, 1).toFloat();
    if (wbg != twb) {
      wbg = twb;
      save = 1;
    }
    twb = split(wb, 2).toFloat();
    if (wbb != twb) {
      wbb = twb;
      save = 1;
    }
  }
  
  // Get color from POST
  if (server.hasArg("color") & onState) {
    String color = server.arg("color");
    r = split(color, 0).toInt();
    g = split(color, 1).toInt();
    b = split(color, 2).toInt();
    setColor(r, g, b);
  }

  // Should the config be saved?
  if (save) {
    saveConf();
  }
  
  // Check API in POST
  if (server.hasArg("api")) {
    server.send (200, "text/plain", "OK");
  }
  else {
    String color = String(r) + "," + String(g) + "," + String(b);
    String checked = "";
    if (onState) {
      checked = "checked";
    }
    
    String temp = (String)""
"<html><head><title>Light controls</title></title><body>"
"<form method='post'>"
"  IP: " + ip[0] + "." + ip[1] + "." + ip[2] + "." + ip[3]+ "<br>"
"  NAME: <input type='text' name='name' value='" + name + "'><br>"
"  STATE: <input type='checkbox' name='on' value='on' " + checked + "><br>"
"  COLOR: <input name='color' type='text' value='" + color +"'><br>"
"  BRIGHTNESS: <input type='text' name='brightness' value='" + brightness + "'><br>"
"  WB: <input type='text' name='wb' value='" + wbr + "," + wbg + "," + wbb + "'><br>"
"  API: <input type='checkbox' name='api' value='api'><br>"
"  <input type='submit' value='Set' name='set'>"
"</form>"
"</body></html>";
    server.send (200, "text/html", temp);
  }
}

void pageStatus() {
  String color = String(r) + "," + String(g) + "," + String(b);
  String temp = ""
"\nNAME:" + name +
"\nCOLOR:" + color +
"\nSTATE:" + onState +
"\nWB:" + wbr + "," + wbg + "," + wbb +
"\nBRIGHTNESS:" + brightness +
"";
  server.send(200, "text/plain", temp);
}

void page404() {
  server.send (404, "text/plain", "404 - Not Found");
}

/*----------------------*/
/*      Functions       */
/* ---------------------*/

// Read device name from config file on FS
void loadConf() {
  File configFile = SPIFFS.open("name.cfg", "r");
  if (!configFile) {
    Serial.println("Can't load config");
  }
  else {
    String temp = configFile.readString();
    configFile.close();
    name = split(temp, 0);
    wbr = split(temp, 1).toFloat();
    wbg = split(temp, 2).toFloat();
    wbb = split(temp, 3).toFloat();
  }
}


// Write device name into config file on FS and set it to global variable
void saveConf() {
  String temp = name + "," + (String)wbr  + "," + (String)wbg  + "," + (String)wbb;
  File configFile = SPIFFS.open("name.cfg", "w");
  if (!configFile) {
    Serial.println("Can't save config");
  }
  else {
    configFile.print(temp);
    configFile.close();
  }
}

// Sets LED color
void setColor(int tr, int tg, int tb) {  
  tr = int((tr * wbr) * ((float)brightness/100));
  tg = int((tg * wbg) * ((float)brightness/100));
  tb = int((tb * wbb) * ((float)brightness/100));

  if (tr > 1023 || tr < 0) { tr = 1023; }
  if (tg > 1023 || tg < 0) { tg = 1023; }
  if (tb > 1023 || tg < 0) { tb = 1023; }
  
  analogWrite(ledRed, tr);
  analogWrite(ledGreen, tg);
  analogWrite(ledBlue, tb);
}

String split(String data, int index) {
    char separator = 44; // 44 -> ","
    int found = 0;
    int strIndex[] = { 0, -1 };
    int maxIndex = data.length() - 1;

    for (int i = 0; i <= maxIndex && found <= index; i++) {
        if (data.charAt(i) == separator || i == maxIndex) {
            found++;
            strIndex[0] = strIndex[1] + 1;
            strIndex[1] = (i == maxIndex) ? i+1 : i;
        }
    }
    return found > index ? data.substring(strIndex[0], strIndex[1]) : "";
}

