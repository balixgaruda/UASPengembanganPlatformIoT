require('dotenv').config();

const express = require('express');
const { createClient } = require('@supabase/supabase-js');
const { mqttClient, publishRelayCommand } = require('./mqttClient');

const supabase = createClient(
    process.env.SUPABASE_URL,
    process.env.SUPABASE_ANON_KEY
);

const app = express();
app.use(express.json());

app.use((req, res, next) => {
    console.log('Request Debug');
    console.log('Method:', req.method);
    console.log('URL:', req.url);
    console.log('Content-Type:', req.headers['content-type']);
    console.log('Body:', req.body);
    next();
});

const PORT = process.env.PORT || 3002;

app.get('/', (req, res) => {
    res.send("IoT Power Monitor API Server");
});

app.get('/monitoring', async (req, res) => {
    try {
        const esp_id = req.query.esp_id || 'ESP32-01';
        console.log(`Monitoring endpoint called for panel: ${esp_id}`);
        
        const { data, error } = await supabase
            .from('sensor_data')
            .select('*')
            .eq('esp_id', esp_id)
            .order('timestamp', { ascending: false })
            .limit(1);
        
        console.log('Supabase query result:', { data, error });
            
        if (error) {
            console.error('Supabase monitoring error:', error);
            return res.status(500).json({ error: error.message });
        }
        
        if (!data || data.length === 0) {
            console.log(`No data found for panel: ${esp_id}`);
            return res.status(404).json({ error: "No sensor data available" });
        }
        
        console.log('Returning data:', data[0]);
        
        res.status(200).json({
            esp_id: data[0].esp_id,
            timestamp: data[0].timestamp,
            power: data[0].power,
            current: data[0].current,
            voltage: data[0].voltage,
            relay_status: data[0].relay_status
        });
    } catch (err) {
        console.error('Monitoring error:', err);
        res.status(500).json({ error: "Failed to fetch data" });
    }
});

app.post('/monitoring', async (req, res) => {
    try {
        const { esp_id, voltage, current, power, relay_status, timestamp } = req.body;
        
        if (!esp_id || voltage === undefined || current === undefined || 
            power === undefined || !relay_status || !timestamp) {
            return res.status(400).json({ 
                error: "Missing required fields",
                required: ["esp_id", "voltage", "current", "power", "relay_status", "timestamp"]
            });
        }
        
        const { data, error } = await supabase
            .from('sensor_data')
            .insert({
                esp_id,voltage,current,power,relay_status,timestamp
            });
        
        if (error) {
            console.error('Supabase insert error:', error);
            return res.status(500).json({ error: error.message });
        }
        
        console.log(`Sensor data stored: ${esp_id} - V:${voltage}V I:${current}A P:${power}W`);
        
        res.status(200).json({
            message: "Sensor data received",
            esp_id,
            status: "OK"
        });
        
    } catch (err) {
        console.error('POST monitoring error:', err);
        res.status(500).json({ error: "Failed to store sensor data" });
    }
});

app.get('/panels', async (req, res) => {
    try {
        const { data, error } = await supabase
            .from('sensor_data')
            .select('esp_id, timestamp, voltage, current, power, relay_status')
            .order('timestamp', { ascending: false });
        
        if (error) {
            return res.status(500).json({ error: error.message });
        }
        
        const panelMap = {};
        data.forEach(record => {
            if (!panelMap[record.esp_id]) {
                panelMap[record.esp_id] = record;
            }
        });
        
        const panels = Object.values(panelMap);
        
        res.status(200).json({
            total_panels: panels.length,
            panels: panels
        });
    } catch (err) {
        console.error('Panels error:', err);
        res.status(500).json({ error: "Failed to fetch panels" });
    }
});


app.get('/usage', async (req, res) => {
    try {
        const { data, error } = await supabase
            .from('usage_history')
            .select('*');
            
        if (error || data.length === 0) {
            return res.status(404).json({ error: "User data not found" });
        }
        
        res.status(200).json(data);
    } catch (err) {
        console.error('Usage error:', err);
        res.status(500).json({ error: "Failed to fetch usage data" });
    }
});

app.post('/relay', async (req, res) => {
    try {
        const {
            relay_id = 'Relay-1',
            command,
            reason = 'MANUAL',
            initiated_by = 'user'
        } = req.body;

        if (!command || !['ON', 'OFF'].includes(command)) {
            return res.status(400).json({
                error: 'Invalid command. Use ON or OFF'
            });
        }

        let esp_id = 'ESP32-01'; // default
        if (relay_id.includes('ESP32')) {
            esp_id = relay_id.replace('Relay-', '');
        }

        if (esp_id === 'ESP32-01') {
            publishRelayCommand(relay_id, command, reason, initiated_by);
        }

        const { data: latestData, error: fetchError } = await supabase
            .from('sensor_data')
            .select('*')
            .eq('esp_id', esp_id)
            .order('timestamp', { ascending: false })
            .limit(1)
            .single();

        if (!fetchError && latestData) {
            const { error: updateError } = await supabase
                .from('sensor_data')
                .update({ relay_status: command })
                .eq('id', latestData.id);

            if (updateError) {
                console.error('Failed to update relay status:', updateError);
            } else {
                console.log(`Updated relay status to ${command} for ${esp_id}`);
            }
        }

        const { error } = await supabase
            .from('relay_log')
            .insert({
                relay_id,command,reason,initiated_by,new_status: command,timestamp: new Date().toISOString()
            });

        if (error) {
            console.error('Supabase relay log error:', error);
            return res.status(500).json({
                error: 'Failed to store relay log'
            });
        }

        return res.status(200).json({
            message: 'Relay command sent successfully',relay_id,command,esp_id,status: 'PUBLISHED'
        });

    } catch (err) {
        console.error('Relay endpoint error:', err);
        return res.status(500).json({
            error: 'Internal server error'
        });
    }
});

app.get('/alerts', async (req, res) => {
    try {
        const { data, error } = await supabase
            .from('alerts')
            .select('*')
            .order('timestamp', { ascending: false });
            
        if (error) {
            return res.status(500).json({ error: "Failed to get alerts" });
        }
        
        res.status(200).json(data);
    } catch (err) {
        console.error('Alerts error:', err);
        res.status(500).json({ error: "Failed to fetch alerts" });
    }
});

app.post('/alert', async (req, res) => {
    try {
        const { alert_type, esp_id, description, suggested_action } = req.body;
        
        if (!alert_type || !esp_id || !description) {
            return res.status(400).json({ message: "Missing required fields" });
        }
        
        const { error } = await supabase
            .from('alerts')
            .insert({
                alert_type,
                esp_id,
                description,
                suggested_action,
                timestamp: new Date().toISOString()
            });
            
        if (error) {
            console.error('Alert insert error:', error);
            return res.status(500).json({ message: "Failed to send alert" });
        }
        
        res.status(200).json({ message: "Alert sent to user" });
    } catch (err) {
        console.error('Alert endpoint error:', err);
        res.status(500).json({ message: "Failed to send alert" });
    }
});

app.get('/debug/sensor-data', async (req, res) => {
    try {
        const { data, error, count } = await supabase
            .from('sensor_data')
            .select('*', { count: 'exact' })
            .order('timestamp', { ascending: false })
            .limit(5);
        
        res.json({
            total_rows: count,
            latest_5_records: data,
            error: error
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});