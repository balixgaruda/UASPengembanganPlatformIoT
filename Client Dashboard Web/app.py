import streamlit as st
import requests
import pandas as pd
import time
from datetime import datetime

API_BASE = "http://192.168.43.243:3002"

st.set_page_config(page_title="IoT Power Monitoring", layout="wide")
st.title("IoT Power Monitoring Dashboard")

if "history" not in st.session_state:
    st.session_state.history = {}  # Dictionary to store history per panel
    for panel in ["ESP32-01", "ESP32-02", "ESP32-03"]:
        st.session_state.history[panel] = pd.DataFrame(
            columns=["timestamp", "voltage", "current", "power"]
        )

if "selected_panel" not in st.session_state:
    st.session_state.selected_panel = "ESP32-01"

st.sidebar.title("Panel Selection")
available_panels = ["ESP32-01", "ESP32-02", "ESP32-03"]
selected_panel = st.sidebar.selectbox(
    "Select Panel to Monitor:",
    available_panels,
    index=available_panels.index(st.session_state.selected_panel)
)

if selected_panel != st.session_state.selected_panel:
    st.session_state.selected_panel = selected_panel
    

st.sidebar.divider()
st.sidebar.subheader("ℹ️ Panel Info")
st.sidebar.info(f"Currently monitoring: **{selected_panel}**")

panel_descriptions = {
    "ESP32-01": "Real Hardware (PZEM Sensor + Relay)",
    "ESP32-02": "Simulated Panel (225V, 0.8A)",
    "ESP32-03": "Simulated Panel (218V, 1.2A)"
}
st.sidebar.caption(panel_descriptions[selected_panel])

def get_latest_data(esp_id):
    try:
        r = requests.get(f"{API_BASE}/monitoring?esp_id={esp_id}", timeout=3)
        if r.status_code == 200:
            return r.json()
        else:
            st.error(f"API Error: {r.status_code} - {r.text}")
    except requests.exceptions.Timeout:
        st.error("Request timeout - server not responding")
    except requests.exceptions.ConnectionError:
        st.error("Connection error - cannot reach server")
    except Exception as e:
        st.error(f"Error: {str(e)}")
    return None

def send_relay(command, esp_id):
    payload = {
        "relay_id": f"Relay-{esp_id}",  
        "command": command,
        "reason": "WEB_DASHBOARD",
        "initiated_by": f"dashboard_{esp_id}"
    }
    try:
        r = requests.post(f"{API_BASE}/relay", json=payload, timeout=3)
        if r.status_code == 200:
            return True
        else:
            st.error(f"Relay command failed: {r.text}")
            return False
    except Exception as e:
        st.error(f"Error sending command: {str(e)}")
        return False

st.subheader(f"Relay Control - {selected_panel}")

if selected_panel in ["ESP32-02", "ESP32-03"]:
    st.warning(f"Note: {selected_panel} is simulated. Relay commands will be logged but no physical relay will switch.")

col1, col2 = st.columns(2)

with col1:
    if st.button("TURN ON", use_container_width=True, type="primary"):
        if send_relay("ON", selected_panel):
            st.success(f"Relay ON command sent to {selected_panel}")

with col2:
    if st.button("TURN OFF", use_container_width=True):
        if send_relay("OFF", selected_panel):
            st.warning(f"Relay OFF command sent to {selected_panel}")

st.divider()

metric_container = st.empty()
chart_container = st.empty()
status_container = st.empty()

while True:
    data = get_latest_data(selected_panel)

    if data:
        ts = data.get("timestamp", "")
        voltage = data.get("voltage", 0)
        current = data.get("current", 0)
        power = data.get("power", 0)
        relay_status = data.get("relay_status", "UNKNOWN")
        esp_id = data.get("esp_id", selected_panel)

        with metric_container.container():
            st.subheader("Real-time Readings")
            c1, c2, c3, c4 = st.columns(4)
            
            c1.metric("Voltage", f"{voltage:.1f} V")
            c2.metric("Current", f"{current:.3f} A")
            c3.metric("Power", f"{power:.1f} W")
            
            if relay_status == "ON":
                c4.metric("Relay", "ON", delta="Active")
            else:
                c4.metric("Relay", "OFF", delta="Inactive")

        new_row = pd.DataFrame([{
            "timestamp": ts,
            "voltage": voltage,
            "current": current,
            "power": power
        }])

        st.session_state.history[selected_panel] = pd.concat(
            [st.session_state.history[selected_panel], new_row],
            ignore_index=True
        ).tail(50)

        with chart_container.container():
            st.subheader("Real-time Trends (Last 50 readings)")
            
            current_history = st.session_state.history[selected_panel]
            
            if len(current_history) > 0:
                chart_data = current_history.copy()
                chart_data = chart_data.set_index("timestamp")
                
                tab1, tab2, tab3 = st.tabs(["Voltage", "Current", "Power"])
                
                with tab1:
                    st.line_chart(chart_data[["voltage"]], use_container_width=True, color="#FF6B6B")
                    col1, col2, col3 = st.columns(3)
                    col1.metric("Avg Voltage", f"{chart_data['voltage'].mean():.1f} V")
                    col2.metric("Max Voltage", f"{chart_data['voltage'].max():.1f} V")
                    col3.metric("Min Voltage", f"{chart_data['voltage'].min():.1f} V")
                
                with tab2:
                    st.line_chart(chart_data[["current"]], use_container_width=True, color="#4ECDC4")
                    col1, col2, col3 = st.columns(3)
                    col1.metric("Avg Current", f"{chart_data['current'].mean():.3f} A")
                    col2.metric("Max Current", f"{chart_data['current'].max():.3f} A")
                    col3.metric("Min Current", f"{chart_data['current'].min():.3f} A")
                
                with tab3:
                    st.line_chart(chart_data[["power"]], use_container_width=True, color="#FFE66D")
                    col1, col2, col3 = st.columns(3)
                    col1.metric("Avg Power", f"{chart_data['power'].mean():.1f} W")
                    col2.metric("Max Power", f"{chart_data['power'].max():.1f} W")
                    col3.metric("Min Power", f"{chart_data['power'].min():.1f} W")
            else:
                st.info(f"No data history for {selected_panel} yet. Waiting for data...")

        with status_container.container():
            st.caption(f"Last updated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
            st.caption(f"Total readings: {len(current_history)} | Panel: {selected_panel}")

    else:
        with status_container.container():
            st.error(f"No data available from server for {selected_panel}")
            st.info("Waiting for sensor data...")

    time.sleep(3)