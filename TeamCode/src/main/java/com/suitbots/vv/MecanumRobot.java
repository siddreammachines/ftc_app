package com.suitbots.vv;

import com.qualcomm.hardware.modernrobotics.ModernRoboticsI2cGyro;
import com.qualcomm.robotcore.hardware.ColorSensor;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.DeviceInterfaceModule;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.I2cAddr;
import com.qualcomm.robotcore.hardware.OpticalDistanceSensor;
import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Locale;

/**
 * Created by Suit Bots on 11/11/2016.
 */

public class MecanumRobot {
    private DcMotor lf, lr, rf, rr, harvester, flipper;
    private ToggleableServo bf, br, dispenser;
    private ModernRoboticsI2cGyro gyro;
    private OpticalDistanceSensor line;
    private ColorSensor color;
    private Telemetry telemetry;
    private DeviceInterfaceModule dim;

    public MecanumRobot(HardwareMap hardwareMap, Telemetry _telemetry) {
        telemetry = _telemetry;
        dim = hardwareMap.deviceInterfaceModule.get("dim");
        lf = hardwareMap.dcMotor.get("lf");
        lr = hardwareMap.dcMotor.get("lr");
        rf = hardwareMap.dcMotor.get("rf");
        rr = hardwareMap.dcMotor.get("rr");
        flipper = hardwareMap.dcMotor.get("flipper");
        harvester = hardwareMap.dcMotor.get("harvester");

        bf = new ToggleableServo(hardwareMap .servo.get("pf"), 0.0, 1.0);
        br = new ToggleableServo(hardwareMap.servo.get("pr"), 0.0, 1.0);
        dispenser = new ToggleableServo(hardwareMap.servo.get("dispenser"), 0.0, .3);

        gyro = (ModernRoboticsI2cGyro)hardwareMap.gyroSensor.get("gyro");
        line = hardwareMap.opticalDistanceSensor.get("line");
        color = hardwareMap.colorSensor.get("color");
        color.enableLed(false);

        rr.setDirection(DcMotorSimple.Direction.REVERSE);
        rf.setDirection(DcMotorSimple.Direction.REVERSE);
        gyro.calibrate();

        lf.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        lr.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        rf.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        rr.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        flipper.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
    }

    public void onStart() {
        bf.set(0.0);
        br.set(0.0);
        dispenser.set(0.0);
        bf.onStart();
        br.onStart();
        dispenser.onStart();
    }

    public void onStop() {
        stopDriveMotors();
        flipper.setPower(0.0);
        harvester.setPower(0.0);
    }

    // Things that need to happen in the teleop loop to accommodate long-running
    // tasks like running the flipper one at a time.
    public void loop() {
        if (isDoneFlipping()) {
            setFlipperPower(0.0);
        }
    }

    public void updateSensorTelemetry() {
        telemetry.addData("Flipping", flipping ? "Yes" : "No");
        telemetry.addData("Gyro",  gyro.getIntegratedZValue());
        telemetry.addData("Line", String.format(Locale.US, "%.2f", line.getRawLightDetected()));
        telemetry.addData("Color", String.format(Locale.US, "r: %d\td: %d", color.red(), color.blue()));
        telemetry.addData("Encoders", String.format(Locale.US, "%d\t%d\t%d\t%d\t%d",
                lf.getCurrentPosition(),
                lr.getCurrentPosition(),
                rf.getCurrentPosition(),
                rr.getCurrentPosition(),
                flipper.getCurrentPosition()));
    }

    // The Flipper
    public static final int ONE_FILPPER_REVOLUTION = (1120 * 22) / 16;
    private boolean flipping = false;
    public void fire() {
        if (flipping) {
            return;
        }
        flipper.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        flipper.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        flipper.setTargetPosition(ONE_FILPPER_REVOLUTION);
        flipper.setPower(1.0);
        flipping = true;
    }

    public boolean isFlipping() { return flipping; }

    public static final int FLIPPER_CLOSE_ENOUGH = 2;
    public boolean isDoneFlipping() {
        return flipping
            && ! flipper.isBusy()
            || FLIPPER_CLOSE_ENOUGH >= Math.abs(ONE_FILPPER_REVOLUTION - flipper.getCurrentPosition());
    }

    public void stopFlipperIfItIsNotFlipping() {
        if (! flipping) {
            flipper.setPower(0.0);
        }
    }

    public void setFlipperPower(double p) {
        if (flipping) {
            flipper.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            flipping = false;
        }
        flipper.setPower(p);
    }

    // Harvesting

    public void setHarvesterPower(double p) {
        harvester.setPower(p);
    }

    // Servo Control

    public void toggleFrontServo() {
        bf.toggle();
    }
    public void toggleBackServo() {
        br.toggle();
    }
    public void toggleDispenser() {
        dispenser.toggle();
    }
    public void setDispenser(boolean x) {
        dispenser.setFirst(x);
    }

    // Sensors

    public double getLineLightReading() {
        return line.getRawLightDetected();
    }
    public boolean isCalibrating(){  return gyro.isCalibrating(); }
    public void resetGyro() {
        gyro.resetZAxisIntegrator();
    }
    public int getHeading() {
        int angle = gyro.getIntegratedZValue() % 360;
        return angle;
    }

    public static final int COLOR_THRESHOLD = 2;
    public boolean colorSensorIsRed() {
        return color.red() > COLOR_THRESHOLD && color.red() > color.blue();
    }

    public AllianceColor getColor() {
        AllianceColor c = color.red() > color.blue() ? AllianceColor.RED : AllianceColor.BLUE;
        return c;
    }

    public boolean colorSensorIsBlue() {
        return color.blue() > COLOR_THRESHOLD && color.blue() > color.red();
    }

    public int getColorAlpha() {
        return color.alpha();
    }

    // Driving

    public void drivePreservingDirection(double translationRadians, double velocity) {
        final int angle = gyro.getIntegratedZValue();
        if (0 != angle) {
            final double rotSpeed = Math.log((double) Math.abs(angle)) * (angle < 0 ? -1.0 : 1.0);
            drive(translationRadians, velocity, rotSpeed);
        }
    }

    /// Maximum absolute value of some number of arguments
    private static double ma(double... xs) {
        double ret = 0.0;
        for (double x : xs) {
            ret = Math.max(ret, Math.abs(x));
        }
        return ret;
    }

    private static class Wheels {
        public double lf, lr, rf, rr;

        public Wheels(double lf, double rf, double lr, double rr) {
            this.lf = lf;
            this.rf = rf;
            this.lr = lr;
            this.rr = rr;
        }
    }

    private Wheels getWheels(double direction, double velocity, double rotationVelocity) {
        final double vd = velocity;
        final double td = direction;
        final double vt = rotationVelocity;

        double s =  Math.sin(td + Math.PI / 4.0);
        double c = Math.cos(td + Math.PI / 4.0);
        double m = Math.max(Math.abs(s), Math.abs(c));
        s /= m;
        c /= m;

        final double v1 = vd * s + vt;
        final double v2 = vd * c - vt;
        final double v3 = vd * c + vt;
        final double v4 = vd * s - vt;

        // Ensure that none of the values go over 1.0. If none of the provided values are
        // over 1.0, just scale by 1.0 and keep all values.
        double scale = ma(1.0, v1, v2, v3, v4);

        return new Wheels(v1 / scale, v2 / scale, v3 / scale, v4 / scale);
    }

    public void drive(double direction, double velocity, double rotationVelocity) {
        Wheels w = getWheels(direction, velocity, rotationVelocity);
        lf.setPower(w.lf);
        rf.setPower(w.rf);
        lr.setPower(w.lr);
        rr.setPower(w.rr);
    }

    /// Shut down all motors
    public void stopDriveMotors() {
        lf.setPower(0.0);
        lr.setPower(0.0);
        rf.setPower(0.0);
        rr.setPower(0.0);
    }

    // Encoder Driving

    // Assuming 4" wheels
    private static final double TICKS_PER_INCH = 1140 / (Math.PI * 4.0);
    private static final double TICKS_PER_CM = TICKS_PER_INCH / 2.54;
    private static final double ENCODER_DRIVE_POWER = .35;

    private void setMode(DcMotor.RunMode mode, DcMotor... ms) {
        for (DcMotor m : ms) {
            m.setMode(mode);
        }
    }

    private void setPower(double p, DcMotor... ms) {
        for (DcMotor m : ms) {
            m.setPower(p);
        }
    }

    private void setTargetPosition(int pos, DcMotor... ms) {
        for (DcMotor m : ms) {
            m.setTargetPosition(pos);
        }
    }

    private boolean busy(DcMotor... ms) {
        int count = 0;
        for (DcMotor m : ms) {
            if (m.getMode() == DcMotor.RunMode.RUN_TO_POSITION && m.isBusy()) {
                ++count;
            }
        }
        return count == ms.length;
    }

    public boolean driveMotorsBusy() {
        return busy(lf, lr, rf, rr);
    }

    public void encoderDriveTiles(double direction, double tiles) {
        encoderDriveInches(direction, (int)(24.0 * tiles));
    }

    public void encoderDriveInches(double direction, double inches) {
        direction %= Math.PI * 2.0;
        final Wheels w = getWheels(direction, 1.0, 0.0);
        final int ticks = (int)(inches * TICKS_PER_INCH);
        encoderDrive(ticks * w.lf, ticks * w.rf, ticks * w.lr, ticks * w.rr);
    }

    public void encoderDriveCM(double direction, double cm) {
        direction %= Math.PI * 2.0;
        final Wheels w = getWheels(direction, 1.0, 0.0);
        final int ticks = (int)(cm * TICKS_PER_CM);
        encoderDrive(ticks * w.lf, ticks * w.rf, ticks * w.lr, ticks * w.rr);
    }

    private void encoderDrive(double lft, double rft, double lrt, double rrt) {
        encoderDrive((int) lft, (int) rft, (int) lrt, (int) rrt);
    }

    private void encoderDrive(int lft, int rft, int lrt, int rrt) {
        setPower(0.0, lf, lr, rf, rr);
        setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER, lf, lr, rf, rr);
        setTargetPosition(lft, lf);
        setTargetPosition(rft, rf);
        setTargetPosition(lrt, lr);
        setTargetPosition(rrt, rr);
        setMode(DcMotor.RunMode.RUN_TO_POSITION, lf, rf, lr, rr);
        setPower(ENCODER_DRIVE_POWER, lf, lr, rf, rr);
    }

    public void resetDriveMotorModes() {
        setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER, lf, lr, rf, rr);
        setMode(DcMotor.RunMode.RUN_USING_ENCODER, lf, lr, rf, rr);
    }
}
