/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package Robo;

import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.Jaguar;
import edu.wpi.first.wpilibj.Encoder; 
import edu.wpi.first.wpilibj.SmartDashboard;
/**
 *
 * @author paul
 */
public class RoboManipulator extends RoboTask {
    static final double pegBase = 58;   // grabber heigt with arm up and el down
    static final double[] pegHeights = {58, 58, 58, 62, 71, 103, 109};
    static final double[] pegAngles = {110, 84, 73, 0, 0, 0, 0};
    static final double elevatorMaxHeight = 52;
    static final double armMaxAngle = 110;

    static double bufferHeight = 4;
    static double heightAccuracy = 0.5;

    static double bufferAngle = 9;
    static double angleAccuracy = 2;

    private boolean autoMode = false;

    double currentHeight = 0;
    double targetHeight = 0;
    double currentElevatorPower = 0;
    boolean elevatorHeightKnown = false;

    double currentAngle = 0;
    double targetAngle = 0;
    double currentArmPower = 0;
    boolean armAngleKnown = false;

    private double lastSimTime = 0.0;

    boolean armMoving = false;
    boolean elevatorMoving = false;
    
    private boolean trace = false;
    private double elevSpeedScale = 6.0;   // max speed in/sec
    private double armSpeedScale = 30.0;   // max speed deg/sec

    Jaguar rollerbeta = new Jaguar(8);
    Jaguar rolleralpha = new Jaguar(7);
    Jaguar arm = new Jaguar(6);  // 1.0 power = 90RPM....0.5 power = 80 RPM....0.25 power = 48 RPM   NOTE: JAGUAR AND VICTOR POWER VALUES MAY BE DIFFERENT
    Jaguar elevator = new Jaguar(5);     // 1.0 power = 90RPM....0.5 power = 80 RPM....0.25 power = 48 RPM

    public Encoder elevatorEncoder = new Encoder(4, 4, 4, 5);
    public DigitalInput elevatorUp = new DigitalInput(4, 6);
    public DigitalInput elevatorDown = new DigitalInput(4, 13);
    public Encoder armEncoder = new Encoder(4, 8, 4, 7);
    public DigitalInput armUp = new DigitalInput(4, 9);

    // encoder scales
    public double elevatorInchesPerTick = -0.1557;
    public double armDegreesPerTick = 0.4955;
    public double elevatorOffset = 0;
    public double armOffset = 0;

    // tube twister support
    boolean twisterOn = false;
    double twistUntilTime = 0;
    double twistDuration = 1.0;

    RoboManipulator(){
        elevator.setExpiration(15);
        elevator.set(0.0);
        arm.set(0.0);
        rolleralpha.set(0.0);
        rollerbeta.set(0.0);

        elevatorEncoder.setDistancePerPulse(elevatorInchesPerTick);
        
        armEncoder.setDistancePerPulse(armDegreesPerTick);
    }

    void goToPeg(int number){
        moveElevatorTo(pegHeights[number] - pegBase);
        moveArmTo(pegAngles[number]);
    }

    void moveElevatorTo(double height){
        targetHeight = height;
        elevatorMoving = (Math.abs(currentHeight - targetHeight) >= heightAccuracy);
        autoMode = true;
    }

    void moveArmTo(double angle){
        targetAngle = angle;
        armMoving = (Math.abs(currentAngle - targetAngle) >= angleAccuracy);
        autoMode = true;
    }

    void moveArm(double power){
        autoMode = false;
        setArmPower(power);
        targetAngle = armEncoder.getDistance();
    }

    void moveElevator(double power){
        autoMode = false;
        setElevatorPower(power);
        targetHeight = elevatorEncoder.getDistance();
    }

    public void clawGrab(){
        rolleralpha.set(1.0);
        rollerbeta.set(1.0);
    }
    public void clawRelease(){
        rolleralpha.set(-1.0);
        rollerbeta.set(-1.0);
    }
    public void clawStop(){
        rolleralpha.set(0);
        rollerbeta.set(0);
    }
    public void clawTwistUp(){
        rolleralpha.set(1.0);
        rollerbeta.set(-1.0);
    }
    public void clawTwistDown(){
        rolleralpha.set(-1.0);
        rollerbeta.set(1.0);
    }

    public boolean isAuto(){
        return autoMode;
    }

    boolean isMoving(){
        return (armMoving || elevatorMoving);
    }

    public void start(){
        super.start();
        // start the encoders (will not work without this!)
        armEncoder.start();
        elevatorEncoder.start();
        // NO PID control (initially)
        disable();
        arm.set(0);
        elevator.set(0);
        rolleralpha.set(0);
        rollerbeta.set(0);
    }

    public void enable(){
        targetHeight = currentHeight;
        targetAngle = currentAngle;
        autoMode = true;
//        armPid.enable();
//        elevatorPid.enable();
    }

    public void disable(){
        autoMode = false;
//        armPid.disable();
//        elevatorPid.disable();
        arm.set(0);
        elevator.set(0);
    }

    public void zeroEncoders(){
        armOffset = -armEncoder.getDistance();
        elevatorOffset = -elevatorEncoder.getDistance();
    }

    public void handle(){
        super.handle();

        // reset encoders when at limits
        if(!armUp.get()) {
            armOffset = 0;
            currentAngle = 0;
            armEncoder.reset();
        }
        if(!elevatorDown.get()) {
            elevatorOffset = 0;
            currentHeight = 0;
            elevatorEncoder.reset();
        }

        // read height & angle
        currentHeight = elevatorEncoder.getDistance() + elevatorOffset;
        currentAngle = armEncoder.getDistance() + armOffset;

        // SmartDashboard.log(currentHeight, "El. height (in)");
        // SmartDashboard.log(currentAngle, "Arm angle (deg)");

        if(!autoMode) return;

        // adjust elevator height
        double heightDifference = Math.abs(targetHeight - currentHeight);
        double direction = signum(targetHeight - currentHeight);
        double angleDifference = Math.abs(targetAngle - currentAngle);
        double armDirection = -signum(targetAngle - currentAngle);

        boolean armMovingUp = (targetAngle == 0 && armUp.get()) || (armDirection > 0 && angleDifference > angleAccuracy);
        boolean elevatorMoving = heightDifference > heightAccuracy;
        boolean armMovingDown = armDirection < 0 && angleDifference > angleAccuracy;

        // motion priorities:
        // 1. arm up
         // 2. elevator up/down
        // 3. arm down

        if(armMovingUp){
            setElevatorPower(0);
            if(!armUp.get()){
                // limit switch - stop
                setArmPower(0);
            }
            else if(targetAngle == 0 || angleDifference > bufferAngle) {
                // fast up, also if moving to top (targetAngle = 0)
                setArmPower(1.0);
            }
            else if(angleDifference > angleAccuracy) {
                // gradually slow down if going down
                setArmPower(((angleDifference / bufferAngle) * 0.5 + 0.5));
            }
            else {
                // stop
                setArmPower(0);
            }
        }
        else if(elevatorMoving){
            setArmPower(0);
            if(direction > 0 && !elevatorUp.get() || direction < 0 && !elevatorDown.get()){
                // limit switches - stop
                setElevatorPower(0);
            }
            else if(heightDifference > bufferHeight) {
                // fast up / slow down
                if(direction > 0) setElevatorPower(1.0);
                else setElevatorPower(-0.4);
             }
             else if(heightDifference > heightAccuracy) {
                // gradually slow down if going down
                if(direction > 0) setElevatorPower(1);
                else setElevatorPower(direction * ((heightDifference / bufferHeight) * 0.2 + 0.2));
            }
            else {
                // stop
                setElevatorPower(0);
            }
        }
        else if(armMovingDown){
            setElevatorPower(0);
            if(angleDifference > bufferAngle) {
                // slower down
                setArmPower(-0.6);
            }
            else if(angleDifference > angleAccuracy) {
                // gradually slow down if going down
                setArmPower(-(((angleDifference / bufferAngle) * 0.3 + 0.3)));
            }
            else {
                // stop
                setArmPower(0);
            }
        }
        else {
            // no motion - stop
            setElevatorPower(0);
            setArmPower(0);
        }
  }

    private void setElevatorPower(double power){
        currentElevatorPower = power;
        if(!elevatorUp.get() && power > 0) power = 0;
        if(!elevatorDown.get() && power < 0) power = 0;
        elevator.set(-power);
        elevatorMoving = (power != 0);
    }
    private void setArmPower(double power){
        currentArmPower = power;
        if(!armUp.get() && power > 0) power = 0;
        arm.set(power);
        armMoving = (power != 0);
    }

    void simulate(){
        double dt = taskTime - lastSimTime;
        lastSimTime = taskTime;

        // simulate motion
        currentHeight  += currentElevatorPower * elevSpeedScale * dt;
        // simulate motion
        currentAngle  += currentArmPower * armSpeedScale * dt;
    }
}
