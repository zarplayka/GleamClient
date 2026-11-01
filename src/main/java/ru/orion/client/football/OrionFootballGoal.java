package ru.orion.client.football;

import ru.orion.client.basketball.OrionBasketballVector;

/** Локальная система координат ворот: положительный Z направлен в сетку. */
public record OrionFootballGoal(OrionBasketballVector base, double yaw, double scale) {
    public static final double WIDTH = 7.32;
    public static final double HEIGHT = 2.44;
    public static final double DEPTH = 2.0;
    public static final double POST_RADIUS = .065;
    public OrionFootballGoal {
        if (base == null || !Double.isFinite(base.x()) || !Double.isFinite(base.y()) || !Double.isFinite(base.z())
                || !Double.isFinite(yaw) || !Double.isFinite(scale) || scale < .25 || scale > 3)
            throw new IllegalArgumentException("Некорректные ворота");
    }
    public OrionBasketballVector local(OrionBasketballVector world) {
        var p=world.subtract(base);double c=Math.cos(yaw),s=Math.sin(yaw);
        return new OrionBasketballVector((p.x()*c-p.z()*s)/scale,p.y()/scale,(p.x()*s+p.z()*c)/scale);
    }
    public OrionBasketballVector world(OrionBasketballVector local) { return base.add(worldDirection(local).scale(scale)); }
    public OrionBasketballVector worldDirection(OrionBasketballVector v) {
        double c=Math.cos(yaw),s=Math.sin(yaw);return new OrionBasketballVector(v.x()*c+v.z()*s,v.y(),-v.x()*s+v.z()*c);
    }
    public boolean crossedLine(OrionBasketballVector before,OrionBasketballVector after,double ballRadius) {
        var a=local(before);var b=local(after);double r=ballRadius/scale;
        // Гол только после полного пересечения плоскости всей сферой.
        if (a.z()-r>=0 || b.z()-r<0 || b.z()<=a.z()) return false;
        double t=(r-a.z())/(b.z()-a.z());
        double x=a.x()+(b.x()-a.x())*t,y=a.y()+(b.y()-a.y())*t;
        return Math.abs(x)+r < WIDTH/2-POST_RADIUS && y-r>=0 && y+r < HEIGHT-POST_RADIUS;
    }
}
