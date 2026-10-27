package com.example.town.selection;

import com.example.town.model.PlotBounds;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/** 只发给查看者；先裁剪长边，再按固定密度采样，避免大选区看不清或发送无界粒子。 */
public final class BoundaryParticles {
    private static final double RADIUS = 72;
    private static final double STEP = 0.75;

    private BoundaryParticles() { }

    public static void outline(Player player, PlotBounds bounds, double y, Particle.DustOptions dust) {
        if (!player.getWorld().getName().equals(bounds.world())) return;
        double x1=bounds.minX(), x2=(double)bounds.maxX()+1;
        double z1=bounds.minZ(), z2=(double)bounds.maxZ()+1;
        edge(player,x1,y,z1,x2,y,z1,dust);
        edge(player,x2,y,z1,x2,y,z2,dust);
        edge(player,x2,y,z2,x1,y,z2,dust);
        edge(player,x1,y,z2,x1,y,z1,dust);
    }

    public static void edge(Player player, double ax, double ay, double az,
                            double bx, double by, double bz, Particle.DustOptions dust) {
        Location viewer=player.getLocation();
        double[] range=clip(ax,ay,az,bx,by,bz,viewer.getX(),viewer.getY(),viewer.getZ(),RADIUS);
        if (range == null) return;
        double length=Math.sqrt((bx-ax)*(bx-ax)+(by-ay)*(by-ay)+(bz-az)*(bz-az));
        int steps=Math.max(1,(int)Math.ceil(length*(range[1]-range[0])/STEP));
        for (int i=0;i<=steps;i++) {
            double t=range[0]+(range[1]-range[0])*i/steps;
            player.spawnParticle(Particle.DUST,ax+(bx-ax)*t,ay+(by-ay)*t,az+(bz-az)*t,
                    1,0,0,0,0,dust);
        }
    }

    /** 线段与查看者周围立方体的相交参数，返回值同时限制粒子数量与显示距离。 */
    static double[] clip(double ax,double ay,double az,double bx,double by,double bz,
                         double vx,double vy,double vz,double radius) {
        double start=0,end=1;
        double[] a={ax,ay,az},b={bx,by,bz},v={vx,vy,vz};
        for (int axis=0;axis<3;axis++) {
            double delta=b[axis]-a[axis];
            if (Math.abs(delta)<1e-9) {
                if (Math.abs(a[axis]-v[axis])>radius) return null;
            } else {
                double near=(v[axis]-radius-a[axis])/delta;
                double far=(v[axis]+radius-a[axis])/delta;
                start=Math.max(start,Math.min(near,far));
                end=Math.min(end,Math.max(near,far));
                if (start>end) return null;
            }
        }
        return new double[]{start,end};
    }
}
