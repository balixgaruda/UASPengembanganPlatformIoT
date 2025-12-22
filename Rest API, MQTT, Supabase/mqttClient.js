const mqtt = require('mqtt');
const { supabase } = require('./supabase');

const MQTT_BROKER = 'mqtt://192.168.43.243:1883';
const SENSOR_TOPIC = 'iot/sensor/+';
const RELAY_TOPIC = 'iot/relay/+';

const client = mqtt.connect(MQTT_BROKER);

client.on('connect', () => {
    console.log('MQTT Broker connected');
    
    client.subscribe(SENSOR_TOPIC, (err) => {
        if (err) {
            console.error('Failed to subscribe to sensor topic:', err);
        } else {
            console.log(`Subscribed to ${SENSOR_TOPIC}`);
        }
    });
    
    client.subscribe(RELAY_TOPIC, (err) => {
        if (err) {
            console.error('Failed to subscribe to relay topic:', err);
        } else {
            console.log(`Subscribed to ${RELAY_TOPIC}`);
        }
    });
});

client.on('message', async (topic, message) => {
    console.log(`Received message on topic: ${topic}`);
    console.log(`Raw message: ${message.toString()}`);
    
    try {
        const payload = JSON.parse(message.toString());
        console.log('Parsed payload:', payload);
        if (topic.startsWith('iot/sensor')) {
            const { esp_id, current, voltage, power, relay_status, timestamp } = payload;

            const { data, error } = await supabase
                .from('sensor_data')
                .insert({
                    esp_id, current, voltage, power, relay_status, timestamp
                });

            if (error) {
                console.error('Supabase sensor insert error:', error);
            } else {
                console.log(`Sensor data stored from ${esp_id}`);
                console.log(`   V: ${voltage}V, I: ${current}A, P: ${power}W`);
            }
        }
        if (topic.startsWith('iot/relay')) {
            const { esp_id, relay_id, command, reason, initiated_by, timestamp } = payload;

            const { data, error } = await supabase
                .from('relay_log')
                .insert({
                    esp_id,relay_id,command,reason,initiated_by,new_status: command, timestamp
                });

            if (error) {
                console.error('Relay log insert error:', error);
            } else {
                console.log('Relay event stored: ${relay_id} -> ${command}');
            }
        }

    } catch (err) {
        console.error('MQTT message processing error:', err.message);
        console.error('Full error:', err);
    }
});

client.on('error', (err) => {
    console.error('MQTT connection error:', err);
});

client.on('close', () => {
    console.log('MQTT connection closed');
});

client.on('offline', () => {
    console.log('MQTT client offline');
});

/**
 * @param {string} relay_id 
 * @param {string} command 
 * @param {string} reason 
 * @param {string} initiated_by 
 */

function publishRelayCommand(relay_id, command, reason = 'MANUAL', initiated_by = 'user') {
    const topic = `iot/command/ESP32-01`; 
    const payload = JSON.stringify({
        relay_id,command,reason,initiated_by, timestamp: new Date().toISOString()
    });
    
    client.publish(topic, payload, { qos: 1 }, (err) => {
        if (err) {
            console.error(`Failed to publish to ${topic}:`, err);
        } else {
            console.log('MQTT published → ${topic}: ${payload}');
        }
    });
}

module.exports = {
    mqttClient: client,
    publishRelayCommand
};