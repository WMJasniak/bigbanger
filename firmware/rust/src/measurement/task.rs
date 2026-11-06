use embassy_executor::task;
use embassy_sync::mutex::Mutex;
use embassy_time::Timer;
use embassy_sync::blocking_mutex::raw::CriticalSectionRawMutex;
use esp_hal::gpio::{Input, Output};
use alloc::format;
use crate::measurement::{MeasurementCommand, MEASUREMENT_CMD, MEASUREMENT_DATA};
use crate::utils::*;
use super::HX711BB;

#[task]
pub async fn start_measurement_task(load_sensor: &'static Mutex<CriticalSectionRawMutex, HX711BB<'static, Output<'static>, Input<'static>>>) {
    loop {
        match MEASUREMENT_CMD.wait().await {
            MeasurementCommand::Start => {
                {
                    let mut sensor = load_sensor.lock().await;
                    sensor.start_now();
                }
                loop {
                    // MEASUREMENT_CMD is a Signal, not a queue -- it only ever holds the
                    // latest command. A Tare sent while already streaming used to be
                    // picked up here, found not to be Stop, and silently dropped, so
                    // the tare button did nothing at all while a measurement was live
                    // (the only state Free Measure's screen ever leaves it in, since it
                    // auto-starts on connect and has no stop button). Act on Tare here
                    // too instead of discarding it.
                    match MEASUREMENT_CMD.try_take() {
                        Some(MeasurementCommand::Stop) => break,
                        Some(MeasurementCommand::Tare) => {
                            debug_info("Taring the load sensor");
                            let mut sensor = load_sensor.lock().await;
                            sensor.tare(16);
                        }
                        _ => {}
                    }
                    let mut sensor = load_sensor.lock().await;
                    let packet = sensor.get_weight_packet().await;
                    MEASUREMENT_DATA.send(packet).await;
                }
                    Timer::after_millis(10).await;
            }
            MeasurementCommand::Stop => {}
            MeasurementCommand::Tare => {
                debug_info("Taring the load sensor");
                let mut sensor = load_sensor.lock().await;
                sensor.tare(16);
            }
        }
    }
}

#[task]
pub async fn run_calibration(load_sensor: &'static Mutex<CriticalSectionRawMutex, HX711BB<'static, Output<'static>, Input<'static>>>, mut button: Input<'static>, mut led: Output<'static>, millis: u32) {
    loop {
        if press_for_millis(&button, millis) {
            led.set_high();
            debug_info("Entering calibration..");
            let mut sensor = load_sensor.lock().await;
            sensor.init_calibration();
            button.wait_for_rising_edge().await;
            Timer::after_millis(3).await;
            button.wait_for_falling_edge().await;
            Timer::after_millis(500).await;
            sensor.calibrate();
            led.set_low();
            Timer::after_millis(1000).await;
        } else {
            Timer::after_millis(100).await;
        }
    }
}
