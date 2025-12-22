"""
Dummy data generator for testing multi-panel IoT system
Simulates ESP32-02 and ESP32-03 sending sensor data to the API
ESP32-01 is real hardware and should not be simulated
"""

import requests
import time
import random
from datetime import datetime

API_BASE = "http://192.168.43.243:3002"

# Only simulate ESP32-02 and ESP32-03 (ESP32-01 is real hardware)
PANELS = {
    "ESP32-02": {
        "base_voltage": 225.0,
        "base_current": 0.8,
        "variation": 0.15,  # 15% variation
        "relay_status": "ON"
    },
    "ESP32-03": {
        "base_voltage": 218.0,
        "base_current": 1.2,
        "variation": 0.2,  # 20% variation
        "relay_status": "ON"
    }
}

def generate_sensor_data(panel_id):
    """Generate realistic sensor data for a panel"""
    panel = PANELS[panel_id]
    
    # Add random variation
    voltage = panel["base_voltage"] * (1 + random.uniform(-panel["variation"], panel["variation"]))
    current = panel["base_current"] * (1 + random.uniform(-panel["variation"], panel["variation"]))
    power = voltage * current
    
    return {
        "esp_id": panel_id,
        "voltage": round(voltage, 1),
        "current": round(current, 3),
        "power": round(power, 1),
        "relay_status": panel["relay_status"],
        "timestamp": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
    }

def send_sensor_data(data):
    """Send sensor data to API which stores it in Supabase"""
    try:
        # POST to /monitoring endpoint to store in database
        r = requests.post(f"{API_BASE}/monitoring", json=data, timeout=3)
        if r.status_code == 200:
            print(f"✓ {data['esp_id']}: V={data['voltage']}V, I={data['current']}A, P={data['power']}W → Stored in DB")
            return True
        else:
            print(f"✗ Failed {data['esp_id']} → Status {r.status_code}: {r.text}")
            return False
    except requests.exceptions.ConnectionError:
        print(f"✗ Connection error - Is the server running at {API_BASE}?")
        return False
    except Exception as e:
        print(f"✗ Error sending {data['esp_id']}: {e}")
        return False

def simulate_relay_event(panel_id, command):
    """Simulate a relay event"""
    payload = {
        "relay_id": f"Relay-{panel_id}",  # Format: Relay-ESP32-02, Relay-ESP32-03
        "command": command,
        "reason": "SIMULATION",
        "initiated_by": "dummy_script"
    }
    
    try:
        r = requests.post(f"{API_BASE}/relay", json=payload, timeout=3)
        if r.status_code == 200:
            PANELS[panel_id]["relay_status"] = command
            print(f"✓ {panel_id} relay → {command}")
            return True
        else:
            print(f"✗ Relay command failed: {r.status_code}")
    except Exception as e:
        print(f"✗ Relay error: {e}")
    return False

def main():
    print("=" * 70)
    print("Multi-Panel IoT Power Monitoring Simulator")
    print("=" * 70)
    print(f"Simulating {len(PANELS)} dummy panels: {', '.join(PANELS.keys())}")
    print("ESP32-01 is real hardware and NOT simulated")
    print(f"Sending data to: {API_BASE}/monitoring")
    print(f"Data will be stored in Supabase sensor_data table")
    print("Press Ctrl+C to stop\n")
    
    # Test connection first
    print("Testing API connection...")
    try:
        r = requests.get(f"{API_BASE}/", timeout=3)
        if r.status_code == 200:
            print("✓ API server is reachable\n")
        else:
            print(f"⚠ API responded with status {r.status_code}\n")
    except:
        print(f"✗ Cannot connect to {API_BASE}")
        print("Make sure your Node.js server is running!")
        return
    
    iteration = 0
    
    try:
        while True:
            iteration += 1
            print(f"\n--- Iteration {iteration} ({datetime.now().strftime('%H:%M:%S')}) ---")
            
            # Generate and send data for each dummy panel
            for panel_id in PANELS.keys():
                data = generate_sensor_data(panel_id)
                send_sensor_data(data)
            
            # Every 20 iterations, randomly toggle a relay
            if iteration % 20 == 0:
                panel = random.choice(list(PANELS.keys()))
                current_status = PANELS[panel]["relay_status"]
                new_command = "OFF" if current_status == "ON" else "ON"
                print(f"\n🔄 Toggling {panel} relay: {current_status} → {new_command}")
                simulate_relay_event(panel, new_command)
            
            time.sleep(3)  # Send data every 3 seconds
            
    except KeyboardInterrupt:
        print("\n\n" + "=" * 70)
        print("Simulation stopped by user")
        print(f"Total iterations: {iteration}")
        print(f"Total data points sent: {iteration * len(PANELS)}")
        print("=" * 70)

if __name__ == "__main__":
    main()