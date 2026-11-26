#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <Wire.h>
#include <ESP32Servo.h>

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

float anguloActual = 88.0;
bool giroHaciaLaDerecha = false;

float offsetAccelY = 0.0;

void setup() {
  Serial.begin(115200);

  pinMode(pinIN1, OUTPUT);
  pinMode(pinIN2, OUTPUT);
  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);
  pinMode(enAPin, OUTPUT);

  servoTimon.attach(servoPin);
  servoTimon.write(88);

  Wire.begin(I2C_SDA, I2C_SCL);

  if(!mpu.begin(0x68, &Wire)) {
    Serial.println("No se pudo encontrar el MPU6050");
  } else {
    Serial.println("MPU6050 conectado con exito");
  }

  mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
  mpu.setGyroRange(MPU6050_RANGE_500_DEG);
  mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);

  digitalWrite(pinIN1, LOW);
  digitalWrite(pinIN2, LOW);
  analogWrite(enAPin, 0);

  Serial.println("Sistema encendido. Centrando timón y esperando 3 segundos");
  delay(3000);

  Serial.println("Iniciando navegación autónoma con velocidad controlada...");

  digitalWrite(pinIN1, HIGH);
  digitalWrite(pinIN2, LOW);


  for (int velocidad = 0; velocidad <= 180; velocidad += 10){
    analogWrite(enAPin, velocidad);
    delay(100);
  }
}

void loop() {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);

  long duracion = pulseIn(echoPin, HIGH, 30000);
  int distancia = (duracion == 0) ? 999 : (duracion * 0.034 / 2 );
  
  int anguloDeseado = 88;

  if (distancia < 25) {
    Serial.println("OBSTACULO DETECTADO, Esquivando...");
    anguloDeseado = 80;

  } else {
    sensors_event_t a, g, temp;
    mpu.getEvent(&a, &g, &temp);

    float accelY = a.acceleration.y - offsetAccelY;

    if (accelY > -0.6 && accelY < 0.6){
      anguloDeseado = 88;
    } else {
      anguloDeseado = map((long)(accelY * 10), -15, 15, 80, 100);
      anguloDeseado = constrain(anguloDeseado, 80, 100);
    }
  }

  anguloActual = anguloActual + (anguloDeseado - anguloActual) * 0.12;
  servoTimon.write((int) anguloActual);

  Serial.print("Distancia: "); Serial.print(distancia);
  Serial.print("cm | Timón: "); Serial.println((int) anguloActual);

  delay(50);
}