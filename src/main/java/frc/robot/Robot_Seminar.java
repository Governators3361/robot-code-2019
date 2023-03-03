package frc.robot;

import edu.wpi.first.wpilibj.IterativeRobot;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.DoubleSolenoid.Value;
import edu.wpi.first.wpilibj.Spark;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.Solenoid;
import edu.wpi.first.wpilibj.Compressor;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.wpilibj.CameraServer;
import edu.wpi.cscore.VideoSink;

// (Unused imports)
//import edu.wpi.first.wpilibj.DigitalInput;
//import edu.wpi.first.wpilibj.Servo;
//import edu.wpi.first.wpilibj.Talon;

//import edu.wpi.first.wpilibj.vision.VisionThread;
//import org.opencv.core.Rect;
//import org.opencv.imgproc.Imgproc;

/**
 * The VM is configured to automatically run this class, and to call the
 * functions corresponding to each mode, as described in the IterativeRobot
 * documentation. If you change the name of this class or the package after
 * creating this project, you must also update the manifest file in the resource
 * directory.
 * 
 * Never put the locknuts on the inside ever again
 */
public class Robot extends IterativeRobot {
	final String defaultAuto = "Default";
	final String customAuto = "Auto Dissent (WIP)";
	String autoSelected;
	final double DEFAULT_M_CONSTANT = 1;
	final double HALF_M_CONSTANT = 0.5;
	SendableChooser<Double> speedChooser = new SendableChooser<>();
	SendableChooser<String> autonomousChooser = new SendableChooser<>();

	static Limelight limelight = new Limelight();
	
	//Disabled Vision code
	//NetworkTable network;
	//private VisionThread visionThread;
	//private final Object imgLock = new Object();
	
	static Joystick rightStick;
	static Joystick leftStick;
	
	//Drive motors
	static Spark leftBackMotor;
	static Spark rightBackMotor;
	static Spark leftFrontMotor;
	static Spark rightFrontMotor;

	//toggle variables and lift lock
	static boolean drivingBackward, lastTriggerDirection, lastTriggerManip, 
		lastTriggerLift1, lastTriggerLift2, lastTriggerExtrusion, isLift1, 
		isLift2, canLift, lastCanLift, manipOut;

	//pneumatics
	static DoubleSolenoid liftPiston1, liftPiston2, manipulatorExtrusion;
	static Solenoid manipulator;
	static Compressor comp;
	
	//keep track of whether autonomous routines have run
	boolean autoRan, climbRunning;
	
	static UsbCamera camera, camera2;
	static VideoSink server;

	static Timer timer;
	
	//Motor speed control and value to sent to dashboard
	static double motorConstant;
	static double driveSpeed;
	
	@Override
	public void robotInit() {
		//manage dashboard
		speedChooser.addDefault("Full Speed", DEFAULT_M_CONSTANT);
		speedChooser.addObject("Half Speed", HALF_M_CONSTANT);
		speedChooser.addObject("Tiny Baby Man", .35);
		SmartDashboard.putData("Speed Setting", speedChooser);
		SmartDashboard.putNumber("Average Motor Speed", driveSpeed);
		
		autonomousChooser.addDefault("Default Storm Period", defaultAuto);
		autonomousChooser.addObject("Unfinished auto", customAuto);
		SmartDashboard.putData("Auto choices", autonomousChooser);

		//post to smart dashboard periodically (Limelight variables)
		SmartDashboard.putNumber("LimelightX", limelight.getX());
		SmartDashboard.putNumber("LimelightY", limelight.getY());
		SmartDashboard.putNumber("LimelightArea", limelight.getArea());
		
		// set motor parameters (after declaring globally)
		rightBackMotor = new Spark(2);
		rightFrontMotor = new Spark(3);
		leftBackMotor = new Spark(0);
		leftFrontMotor = new Spark(1);
		
		//DoubleSolenoids take two ports on the PCM
		liftPiston1 = new DoubleSolenoid(0, 1);
		liftPiston2 = new DoubleSolenoid(2, 3);
		manipulatorExtrusion = new DoubleSolenoid(4,5);
		manipulator = new Solenoid(6);
	
		//Compressor must have ClosedLoopControl set to true
		comp = new Compressor(0);
		comp.setClosedLoopControl(true);
		comp.start();
		
		//SmartDashboard speed variable c heck
		motorConstant = speedChooser.getSelected();
		
		//Controls
		rightStick = new Joystick(1);
		leftStick = new Joystick(0);
		
		//set all toggle variables to false
		drivingBackward = false;
		lastTriggerDirection = false;
		lastTriggerManip = false;
		lastTriggerLift1 = false;
		lastTriggerLift2 = false;
		lastTriggerExtrusion = false;

		//set pneumatic lock and monitoring variables to 0
		isLift1 = false;
		isLift2 = false;
		canLift = false;
		lastCanLift = false;

		manipOut = false;

		//set to true to disable activating unfinished climb routine
		climbRunning = true;

		//cameras don't seem to want their resolution set to what I WANT....
		camera = CameraServer.getInstance().startAutomaticCapture(0);
		camera.setResolution(160, 120);
		camera.setFPS(10);
		camera2 = CameraServer.getInstance().startAutomaticCapture(1);
		camera2.setResolution(160, 120);
		camera2.setFPS(10);
		server = CameraServer.getInstance().getServer();

		// Disabled vision code
//		visionThread = new VisionThread(camera, new GripPipeline(), pipeline -> {
//	        if (!pipeline.findContoursOutput().isEmpty()) {
//	            Rect r = Imgproc.boundingRect(pipeline.findContoursOutput().get(0));
//	            synchronized (imgLock) {
//	                centerX = r.x + (r.width / 2);
//        			centerY = r.y + (r.height / 2);
//	            }
//	        }
//	    });
//	    visionThread.start();
	}

	@Override
	public void autonomousInit() {
		autoSelected = autonomousChooser.getSelected();
		System.out.println("Auto selected: " + autoSelected);
		autoRan = false;
	}

	/**
	 * This function is called periodically during autonomous
	 */
	@Override
	public void autonomousPeriodic() {
		//switch statement holds each possible auto choice (more can be added!s)
		switch (autoSelected) {
		case customAuto:
			//autoran will be set to true after the routine has run and this will stop it
			if (!autoRan) {
				leftFrontMotor.set(0.4);
				leftBackMotor.set(0.4);
				rightFrontMotor.set(-0.4);
				rightBackMotor.set(-0.4);
				//Motors will run until they are set to 0!
				Timer.delay(4.5);
				leftFrontMotor.set(0);
				leftBackMotor.set(0);
				rightFrontMotor.set(0);
				rightBackMotor.set(0);
				System.out.println("Done.");
				//Autonomous will run over and over uncontrollably unless you use a variable to
				// run it once and stop it from running it again
				autoRan = true;
			} else {
				System.out.println("Autonomous Completed.");
				Timer.delay(2);
			}
			break;
		case defaultAuto:

		default:
		motorConstant = speedChooser.getSelected();

		//A method to toggle drive direction on button press, otherwise runs over and over!!!
		if(leftStick.getRawButton(11) && !lastTriggerDirection) 
			drivingBackward = !drivingBackward;
     	lastTriggerDirection = leftStick.getRawButton(11);
		SmartDashboard.putBoolean("Driving backwards?",drivingBackward);

		//change spedometer ; )
		driveSpeed = Math.abs((leftFrontMotor.getSpeed() - rightFrontMotor.getSpeed()) / 2);
		SmartDashboard.putNumber("Average Motor Speed", driveSpeed);
		
		//Turbo drive (checks whether direction is switched)
		if(!drivingBackward) {
			if (rightStick.getTrigger()) {
				leftFrontMotor.set(-leftStick.getY() * 1*(motorConstant));
				leftBackMotor.set(-leftStick.getY() * 1*(motorConstant));
				rightFrontMotor.set(rightStick.getY() * 1*(motorConstant));
				rightBackMotor.set(rightStick.getY() * 1*(motorConstant));
			} else {
				leftFrontMotor.set(-leftStick.getY() * .5*(motorConstant));
				leftBackMotor.set(-leftStick.getY() * .5*(motorConstant));
				rightFrontMotor.set(rightStick.getY() * .5*(motorConstant));
				rightBackMotor.set(rightStick.getY() * .5*(motorConstant));
			}
		} else {
			if (rightStick.getTrigger()) {
				leftFrontMotor.set(-rightStick.getY() * -1*(motorConstant));
				leftBackMotor.set(-rightStick.getY() * -1*(motorConstant));
				rightFrontMotor.set(leftStick.getY() * -1*(motorConstant));
				rightBackMotor.set(leftStick.getY() * -1*(motorConstant));
			} else {
				leftFrontMotor.set(-rightStick.getY() * -.5*(motorConstant));
				leftBackMotor.set(-rightStick.getY() * -.5*(motorConstant));
				rightFrontMotor.set(leftStick.getY() * -.5*(motorConstant));
				rightBackMotor.set(leftStick.getY() * -.5*(motorConstant));
			}
		}
		//lower and raise manipulator lever on left stick 3
		if(leftStick.getRawButton(3) && !lastTriggerManip) {
			manipOut = !manipOut;
		}
		lastTriggerManip = leftStick.getRawButton(3);
		manipulator.set(manipOut);
		
		//extrude and retract manipulator on left stick button 4
		if(leftStick.getRawButton(4) && !lastTriggerExtrusion) {
			if(manipulatorExtrusion.get() == DoubleSolenoid.Value.kForward)
				manipulatorExtrusion.set(DoubleSolenoid.Value.kReverse);
			else
				manipulatorExtrusion.set(DoubleSolenoid.Value.kForward);
		}
		lastTriggerExtrusion = leftStick.getRawButton(4);
			break;
		}
	}

	/**
	 * This function is called periodically during operator control
	 */
	@Override
	public void teleopPeriodic() {
		motorConstant = speedChooser.getSelected();

		//A method to toggle drive direction on button press, otherwise runs over and over!!!
		if(leftStick.getRawButton(11) && !lastTriggerDirection) 
			drivingBackward = !drivingBackward;
     	lastTriggerDirection = leftStick.getRawButton(11);
		SmartDashboard.putBoolean("Driving backwards?",drivingBackward);

		//change spedometer ; )
		driveSpeed = Math.abs((leftFrontMotor.getSpeed() - rightFrontMotor.getSpeed()) / 2);
		SmartDashboard.putNumber("Average Motor Speed", driveSpeed);
		
		//Turbo drive (checks whether direction is switched)
		if(!drivingBackward) {
			if (rightStick.getTrigger()) {
				leftFrontMotor.set(-leftStick.getY() * 1*(motorConstant));
				leftBackMotor.set(-leftStick.getY() * 1*(motorConstant));
				rightFrontMotor.set(rightStick.getY() * 1*(motorConstant));
				rightBackMotor.set(rightStick.getY() * 1*(motorConstant));
			} else {
				leftFrontMotor.set(-leftStick.getY() * .5*(motorConstant));
				leftBackMotor.set(-leftStick.getY() * .5*(motorConstant));
				rightFrontMotor.set(rightStick.getY() * .5*(motorConstant));
				rightBackMotor.set(rightStick.getY() * .5*(motorConstant));
			}
		} else {
			if (rightStick.getTrigger()) {
				leftFrontMotor.set(-rightStick.getY() * -1*(motorConstant));
				leftBackMotor.set(-rightStick.getY() * -1*(motorConstant));
				rightFrontMotor.set(leftStick.getY() * -1*(motorConstant));
				rightBackMotor.set(leftStick.getY() * -1*(motorConstant));
			} else {
				leftFrontMotor.set(-rightStick.getY() * -.5*(motorConstant));
				leftBackMotor.set(-rightStick.getY() * -.5*(motorConstant));
				rightFrontMotor.set(leftStick.getY() * -.5*(motorConstant));
				rightBackMotor.set(leftStick.getY() * -.5*(motorConstant));
			}
		}
		
		//Enable and disable control of lift pistons right stick button 11
		if(rightStick.getRawButton(11) && !lastCanLift)
			canLift = !canLift;
		lastCanLift = rightStick.getRawButton(11);

		//Control lift pistons right stick (3 out and in front, 2 out and in back)
		if(rightStick.getRawButton(3) && !lastTriggerLift1 && canLift) {
			if(!isLift1) {
				liftPiston1.set(DoubleSolenoid.Value.kReverse);
				isLift1 = true;
			}
			else {
				liftPiston1.set(DoubleSolenoid.Value.kForward);
				isLift1 = false;
			}
		}
		lastTriggerLift1 = rightStick.getRawButton(3);
		
		if(rightStick.getRawButton(2) && !lastTriggerLift2 && canLift) {
			if(!isLift2) {
				liftPiston2.set(DoubleSolenoid.Value.kReverse);
				isLift2 = true;
			}
			else {
				liftPiston2.set(DoubleSolenoid.Value.kForward);
				isLift2 = false;
			}
		}
		lastTriggerLift2 = rightStick.getRawButton(2);

		if(isLift2) {
			liftPiston2.set(DoubleSolenoid.Value.kReverse);
		} else {
			liftPiston2.set(DoubleSolenoid.Value.kForward);
		}

		//Display lift piston status on dashboard
		SmartDashboard.putBoolean("Lift usable?", canLift);
		SmartDashboard.putBoolean("Lift 1 Extended?", isLift1);
		SmartDashboard.putBoolean("Lift 2 Extended?", isLift2);

		//lower and raise manipulator lever on left stick 3
		if(leftStick.getRawButton(3) && !lastTriggerManip) {
			manipOut = !manipOut;
		}
		lastTriggerManip = leftStick.getRawButton(3);
		manipulator.set(manipOut);
		
		//extrude and retract manipulator on left stick button 4
		if(leftStick.getRawButton(4) && !lastTriggerExtrusion) {
			if(manipulatorExtrusion.get() == DoubleSolenoid.Value.kForward)
				manipulatorExtrusion.set(DoubleSolenoid.Value.kReverse);
			else
				manipulatorExtrusion.set(DoubleSolenoid.Value.kForward);
		}
		lastTriggerExtrusion = leftStick.getRawButton(4);
	
		//activates climb routine (wont work now, for safety!)
		if(leftStick.getRawButton(8) && !climbRunning) {
			climbRunning = true;
			climbRoutine();
		}

	}

	/**
	 * This function is called periodically during test mode
	 */
	@Override
	public void testPeriodic() {

		leftFrontMotor.set(-leftStick.getY() * 1*(motorConstant));
		leftBackMotor.set(-leftStick.getY() * 1*(motorConstant));
		rightFrontMotor.set(rightStick.getY() * 1*(motorConstant));
		rightBackMotor.set(rightStick.getY() * 1*(motorConstant));
		
		if(leftStick.getRawButton(3)) 
			liftPiston1.set(DoubleSolenoid.Value.kForward);
		else if(leftStick.getRawButton(2)) 
			liftPiston1.set(DoubleSolenoid.Value.kReverse);
		else
			liftPiston1.set(DoubleSolenoid.Value.kOff);
		
	}

	//one can write their own methods, of course, if one enjoys not writing a ton of code.
	public void climbRoutine() {
		leftFrontMotor.set(0.35);
		leftBackMotor.set(0.35);
		rightFrontMotor.set(-0.35);
		rightBackMotor.set(-0.35);
		liftPiston1.set(DoubleSolenoid.Value.kForward);

		Timer.delay(4);

		liftPiston1.set(DoubleSolenoid.Value.kReverse);

		Timer.delay(1.5);

		liftPiston2.set(DoubleSolenoid.Value.kForward);

		Timer.delay(3);

		leftFrontMotor.set(0);
		leftBackMotor.set(0);
		rightFrontMotor.set(0);
		rightBackMotor.set(0);

		liftPiston2.set(DoubleSolenoid.Value.kReverse);
	}
	
	//a bunch of methods overidden from WPIlib so that it doesn't get sassy with us
	@Override
	public void disabledInit() {

	}
	@Override
	public void disabledPeriodic() {}
	@Override
	public void teleopInit() {
		manipulator.set(true);
	}
	@Override
	public void robotPeriodic() {}
}
