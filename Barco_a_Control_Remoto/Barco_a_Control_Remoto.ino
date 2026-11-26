#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <Wire.h>
#include <ESP32Servo.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>

Servo servoTimon;
Adafruit_MPU6050 mpu;

#define I2C_SDA 8
#define I2C_SCL 9

const int servoPin = 3;
const int pinIN1 = 4;
const int pinIN2 = 5;
const int trigPin = 6;
const int echoPin = 7;
const int enAPin = 2;

#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"

BLEServer *pServer = NULL;
bool dispositivoConectado = false;


int comandoMotor = 0;       
int velocidadActual = 0;    
const int PWM_MIN = 120;    
const int PWM_MAX = 190;    
float anguloActual = 88.0;
float offsetAccelY = 0.0;
unsigned long ultimoComandoMillis = 0;

class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *server) {
    dispositivoConectado = true;
    Serial.println("\n>> CONECTADO");
  }
  
  void onDisconnect(BLEServer *server) {
    dispositivoConectado = false;
    comandoMotor = 0; 
    Serial.println("\n>> DESCONECTADO");
    delay(300);
    BLEDevice::startAdvertising();
  }
};

class MyCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    String rxValue = pCharacteristic->getValue();
    rxValue.trim();

    if (rxValue.length() > 0) {
      char comando = rxValue[0];
      ultimoComandoMillis = millis();

      switch (comando) {
        case 'F': comandoMotor = 1;  break;
        case 'B': comandoMotor = -1; break;
        case 'S': comandoMotor = 0;  break;
        default: break;
      }
    }
  }
};

void setup() {
  Serial.begin(115200);
  delay(1000);

  pinMode(pinIN1, OUTPUT);
  pinMode(pinIN2, OUTPUT);
  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);

  digitalWrite(pinIN1, LOW);
  digitalWrite(pinIN2, LOW);

  servoTimon.attach(servoPin);
  servoTimon.write(88);

  ledcAttach(enAPin, 5000, 8);
  ledcWrite(enAPin, 0);

  Wire.begin(I2C_SDA, I2C_SCL);
  if(!mpu.begin(0x68, &Wire)) {
    Serial.println("No se pudo encontrar el MPU6050");
  } else {
    Serial.println("MPU6050 conectado con éxito");
  }

  mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
  mpu.setGyroRange(MPU6050_RANGE_500_DEG);
  mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);

  BLEDevice::init("Barco_RC_ESP32");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);
  BLECharacteristic *pRx = pService->createCharacteristic(
      CHARACTERISTIC_UUID_RX,
      BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
  );
  
  pRx->setCallbacks(new MyCallbacks());
  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  BLEDevice::startAdvertising();

  Serial.println(">> Sistema listo.");
}

void loop() {

  if (dispositivoConectado && millis() - ultimoComandoMillis > 600) {
    comandoMotor = 0;
  }

  if (comandoMotor == 1) {
    digitalWrite(pinIN1, HIGH);
    digitalWrite(pinIN2, LOW);
    if (velocidadActual < PWM_MIN) {
      velocidadActual = PWM_MIN; 
    } else if (velocidadActual < PWM_MAX) {
      velocidadActual += 15;
      if (velocidadActual > PWM_MAX) velocidadActual = PWM_MAX;
    }
  } else if (comandoMotor == -1) {
    digitalWrite(pinIN1, LOW);
    digitalWrite(pinIN2, HIGH);

    if (velocidadActual < PWM_MIN) {
      velocidadActual = PWM_MIN;
    } else if (velocidadActual < PWM_MAX) {
      velocidadActual += 15;
      if (velocidadActual > PWM_MAX) velocidadActual = PWM_MAX;
    }
  } else {
    velocidadActual = 0;
    digitalWrite(pinIN1, LOW);
    digitalWrite(pinIN2, LOW);
  }
  ledcWrite(enAPin, velocidadActual);

  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);

  long duracion = pulseIn(echoPin, HIGH, 25000);
  int distancia = (duracion == 0) ? 999 : (duracion * 0.034 / 2);
  
  int anguloDeseado = 88;

  if (distancia < 25) {
    anguloDeseado = 80;
  } else {
    sensors_event_t a, g, temp;
    mpu.getEvent(&a, &g, &temp);

    float accelY = a.acceleration.y - offsetAccelY;

    if (accelY > -0.6 && accelY < 0.6) {
      anguloDeseado = 88;
    } else {
      anguloDeseado = map((long)(accelY * 10), -15, 15, 80, 100);
      anguloDeseado = constrain(anguloDeseado, 80, 100);
    }
  }

  anguloActual = anguloActual + (anguloDeseado - anguloActual) * 0.12;
  servoTimon.write((int)anguloActual);

  delay(35);
}