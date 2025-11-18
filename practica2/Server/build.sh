#!/bin/bash

echo "=== Compilando módulo UDP ==="
cd server/udp

# Descargar Gson si no existe
if [ ! -f "gson-2.10.1.jar" ]; then
    echo "Descargando Gson..."
    curl -L -o gson-2.10.1.jar https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
fi

# Compilar clases UDP
echo "Compilando clases Java..."
javac -cp .:gson-2.10.1.jar udp/*.java

if [ $? -eq 0 ]; then
    echo "✓ Módulo UDP compilado exitosamente"
else
    echo "✗ Error al compilar módulo UDP"
    exit 1
fi

cd ../..

echo ""
echo "=== Compilando API Spring Boot ==="
cd server/api

mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo "✓ API compilada exitosamente"
else
    echo "✗ Error al compilar API"
    exit 1
fi

cd ../..

echo ""
echo "=== Compilación completa ==="
echo ""
echo "Para ejecutar:"
echo "1. Transferir canciones: cd server/udp && java -cp .:gson-2.10.1.jar udp.SyncAll"
echo "2. Iniciar API: cd server/api && mvn spring-boot:run"