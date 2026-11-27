# ⛵ The Moby-Dick — Barco Autónomo  y RC(Carrera Naval)

Proyecto de barco The Moby-Dick para competencia de carrera naval . El repositorio incluye dos códigos separados para cargar en el **ESP32-C3 Super Mini** según la modalidad: uno enfocado en la **categoría básica** (navegación autónoma en línea recta con apoyo del timón y evasión de obstáculos) y otro para la **categoría avanzada** (control remoto por Bluetooth desde una aplicación Android en Kotlin).

---

<img width="1200" height="1599" alt="campeon carrera naval" src="https://github.com/user-attachments/assets/457fce76-9789-48af-8121-a56759552b46" />


## 💡 Componentes Utilizados

* **ESP32-C3 Super Mini:** Es el "cerebro" del barco; recibe las órdenes o ejecuta la navegación según el código cargado y coordina motores y sensores.
* **Driver L298N:** Controla el motor principal del barco y entrega energía de **5 V** desde su salida regulada.
* **Motor DC de 9 V:** Impulsa el barco hacia adelante o hacia atrás conectado al driver.
* **Servomotor MG90S:** Mueve el timón para dirigir el barco.
* **Sensor ultrasónico HC-SR04:** Mide la distancia hacia adelante para detectar obstáculos y ayudar a esquivarlos.
* **Sensor MPU6050:** Detecta inclinaciones o movimientos del barco para apoyar el control del timón.
* **Baterías 18650 (pack 3S):** Tres baterías en serie que dan la energía principal al sistema.

---

## Conexiones Principales

| Componente / Cable | Pin del ESP32-C3 | Para qué sirve / Alimentación |
| :--- | :--- | :--- |
| **L298N — ENA** | GPIO 2 | Regula la velocidad del motor principal |
| **Servo MG90S (señal)** | GPIO 3 | Mueve el timón; se alimenta con **5 V** desde el driver L298N |
| **L298N — IN1** | GPIO 4 | Controla el sentido de giro del motor principal |
| **L298N — IN2** | GPIO 5 | Controla el sentido de giro del motor principal |
| **HC-SR04 — TRIG** | GPIO 6 | Envía el pulso para medir distancia; se alimenta con **5 V** desde el driver L298N |
| **HC-SR04 — ECHO** | GPIO 7 | Recibe el rebote para calcular la distancia al obstáculo |
| **MPU6050 — SDA** | GPIO 8 | Envía datos de movimiento; se alimenta con **3.3 V** desde el ESP32-C3 |
| **MPU6050 — SCL** | GPIO 9 | Sincroniza la comunicación con el sensor |
| **Alimentación ESP32-C3** | Pin 5 V | Recibe **5 V** desde la salida del driver L298N |
| **Baterías 3S + Motor DC 9 V** | Entrada del L298N | Las baterías alimentan el driver y el motor; **GND** va unido en todo el circuito |

---

<img width="853" height="1102" alt="Diagrama del Moby Dick" src="https://github.com/user-attachments/assets/9ce2688e-3c5b-4b07-a0c1-1d8373f7d9e1" />

---

<img width="1599" height="1200" alt="Conexiones barco" src="https://github.com/user-attachments/assets/222a0492-3665-462c-923f-6158bcfa4a2d" />

<img width="1200" height="1599" alt="Moby Dick conexiones 2" src="https://github.com/user-attachments/assets/35dc6166-3a62-4fbc-aa27-74a9bcabc818" />

<img width="1200" height="1599" alt="The Moby dick antes de pruebas" src="https://github.com/user-attachments/assets/a1e3eba7-a18f-47ce-975d-00bcc900da6c" />


## Códigos y Funcionamiento por Categoría

El repositorio separa los programas que se inyectan al **ESP32-C3 Super Mini** para que cargues el que corresponda a cada prueba:

### 🥇 Categoría Básica (Modo Autónomo)
* **Código a inyectar:** Firmware de navegación autónoma para el ESP32-C3.
* **Funcionamiento:** **The Moby-Dick** avanza en línea recta apoyándose en la dirección del timón mediante el **servo MG90S** y el **MPU6050**, además de contar con evasión de obstáculos al frente usando el sensor **HC-SR04**.

* > **Nota sobre el sensor HC-SR04:** Finalmente se decidió retirar el sensor ultrasónico **HC-SR04** del montaje porque no fue posible realizar las pruebas pertinentes antes de la competencia para verificar su funcionamiento y asegurar que respondiera bien.


### Categoría Avanzada (Bluetooth y Control Remoto en Kotlin
* **Código a inyectar:** Firmware de control remoto por Bluetooth para el ESP32-C3, junto con la app Android desarrollada en **Kotlin**.
* **Funcionamiento:** El ESP32-C3 se conecta por Bluetooth con la app en Kotlin para manejar el **motor DC** (avance, retroceso y parada con arranque suave y seguridad ante desconexión). Por falta de tiempo durante el desarrollo, no se alcanzó a integrar correctamente el manejo del **servo del timón** desde el control remoto de la app.
## App y Carga del Programa

* **App Android (Kotlin):** Diseñada para enlazar por Bluetooth con el barco y enviar los comandos del motor DC.
* **Recomendación en Android:** Mantener activos el **Bluetooth** y la **Ubicación** con los permisos concedidos a la aplicación para que el enlace funcione sin problemas.
* **Carga de códigos al ESP32-C3:** Se sube desde la computadora por USB el código correspondiente (modo autónomo o modo Bluetooth) antes de cada prueba.


https://github.com/user-attachments/assets/2d808c4b-6a04-49ee-9d92-aaa0fdbb8e73

https://github.com/user-attachments/assets/d8281dc3-af16-482f-af70-cf7252026f1a

<img width="1200" height="1599" alt="The MobyDick" src="https://github.com/user-attachments/assets/8cbe096e-bc38-42da-853b-74b6d2fd4121" />
