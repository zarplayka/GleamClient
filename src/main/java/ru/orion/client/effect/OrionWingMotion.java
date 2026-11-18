package ru.orion.client.effect;

/** Непрерывная фаза взмахов и плавная смена поз при движении. */
public final class OrionWingMotion {
    private double phase;
    private float running, flying, crouching, swimming, air;
    public float rootFold, flap, tipFold, tipLift, rootHeight=1.38F, bodyPitch;
    public void update(float dt,float speed,boolean sprint,boolean flight,boolean crouch,boolean swim,boolean airborne,float viewPitch) {
        float blend=1F-(float)Math.exp(-dt*5.5F);
        running=approach(running,Math.min(1F,speed/(sprint?4.5F:6F)),blend);
        flying=approach(flying,flight?1F:0F,blend);
        crouching=approach(crouching,crouch?1F:0F,blend);
        swimming=approach(swimming,swim?1F:0F,blend);
        air=approach(air,airborne?1F:0F,blend);
        phase+=dt*(1.3D+running*2.2D+flying*2.4D+air*.7D);
        float wave=(float)Math.sin(phase),lag=(float)Math.sin(phase-.65D);
        rootFold=9F+running*23F+crouching*29F-flying*18F+swimming*22F+wave*3F;
        flap=wave*(3F+running*7F+flying*24F+air*7F)+flying*9F;
        tipFold=10F+running*22F+crouching*19F-flying*10F+lag*(5F+flying*12F);
        tipLift=lag*(4F+running*6F+flying*18F);
        rootHeight=1.38F-.21F*crouching-.86F*Math.max(flying,swimming);
        bodyPitch=approach(bodyPitch,flying*(-90F-viewPitch)+swimming*(-75F)+crouching*28F,blend);
    }
    private static float approach(float current,float target,float amount){return current+(target-current)*amount;}
}
