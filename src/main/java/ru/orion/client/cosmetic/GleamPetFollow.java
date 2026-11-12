package ru.orion.client.cosmetic;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;
import java.util.Set;

/** Питомец остаётся в мире и сам прокладывает путь к игроку. */
final class GleamPetFollow {
    private static final double START_DISTANCE = 2.15D;
    private static final double STOP_DISTANCE = 1.3D;
    private static final double RESPAWN_DISTANCE = 12D;
    private static final double EPSILON = 1E-5D;
    private static final int REPATH_TICKS = 6;
    private static final int PATH_RADIUS = 16;
    private static final double FLIGHT_BOX_OFFSET = .25D;
    private static final double FLIGHT_HEIGHT = .85D;
    private static final Vec3[] SPAWN_OFFSETS = {
            new Vec3(-1.2D, 0D, 0D), new Vec3(1.2D, 0D, 0D),
            new Vec3(0D, 0D, -1.2D), new Vec3(0D, 0D, 1.2D)
    };
    private static final CollisionContext COLLISION = CollisionContext.empty();

    private Mob pathingMob;
    private PathFinder pathFinder;
    private Path path;
    private int nextPathTick;
    private boolean direct;
    private Vec3 lastOwner, previous, position;
    private double horizontalSpeed, verticalSpeed;
    private boolean following;
    private float previousYaw, yaw, previousStride, stride, previousMovement, movement;
    private int age, stillTicks;

    record Frame(Vec3 position, float yaw, float stride, float movement, long ageMillis, long actionMillis) { }

    void tick(ClientLevel level, Vec3 owner, GleamPet pet) {
        boolean teleported = lastOwner != null && owner.distanceToSqr(lastOwner) > 36D;
        lastOwner = owner;
        if (position == null || teleported || position.distanceToSqr(owner) > RESPAWN_DISTANCE * RESPAWN_DISTANCE) {
            reset();
            lastOwner = owner;
            position = previous = spawn(level, owner, pet);
            return;
        }

        Vec3 target = pet.grounded() ? owner : owner.add(0D,
                FLIGHT_HEIGHT + Math.sin(age * .13D) * .07D, 0D);
        Vec3 toOwner = target.subtract(position);
        double separation = toOwner.horizontalDistance();
        if (separation <= STOP_DISTANCE + EPSILON && Math.abs(toOwner.y) < .8D) {
            following = false;
            path = null;
            nextPathTick = age;
        } else if (separation >= START_DISTANCE || Math.abs(toOwner.y) >= 1.6D) {
            following = true;
        }

        previous = position;
        previousYaw = yaw;
        previousStride = stride;
        previousMovement = movement;
        Vec3 requested = following ? steering(level, target, separation, pet)
                : new Vec3(0D, pet.grounded() ? 0D : toOwner.y, 0D);
        if (!following) horizontalSpeed = 0D;
        Vec3 step = pet.grounded() ? walk(level, requested) : fly(level, requested);
        position = position.add(step);
        double moved = step.horizontalDistance();
        if (moved > .005D) {
            float heading = (float) Math.toDegrees(Mth.atan2(-step.x, step.z));
            yaw = Mth.rotLerp(.42F, yaw, heading);
        }
        stride += (float) moved * 12F;
        movement = Mth.approach(movement, Math.min(1F, (float) (moved * 5D)), .24F);
        stillTicks = !following && moved < .01D && Math.abs(step.y) < .01D ? stillTicks + 1 : 0;
        age++;
    }

    private Vec3 steering(ClientLevel level, Vec3 owner, double separation, GleamPet pet) {
        if (age >= nextPathTick) {
            nextPathTick = age + REPATH_TICKS;
            direct = clearRoute(level, owner, pet.grounded());
            path = direct ? null : findPath(level, owner, pet.grounded());
        }

        Vec3 goal = owner;
        if (!direct) {
            if (path == null) return Vec3.ZERO;
            while (!path.isDone()) {
                goal = path.getNextEntityPos(pathingMob).add(0D, pet.grounded() ? 0D : -FLIGHT_BOX_OFFSET, 0D);
                Vec3 remaining = goal.subtract(position);
                if (remaining.horizontalDistanceSqr() > .24D * .24D || Math.abs(remaining.y) > .7D) break;
                path.advance();
            }
            if (path.isDone()) return Vec3.ZERO;
        }

        Vec3 delta = goal.subtract(position);
        double distance = delta.horizontalDistance();
        double speed = Math.clamp(pet.walkSpeed() + (separation - START_DISTANCE) * .036D,
                pet.walkSpeed(), pet.catchUpSpeed());
        horizontalSpeed += Math.clamp(speed - horizontalSpeed, -.065D, .065D);
        double step = Math.min(distance, horizontalSpeed);
        if (direct && Math.abs(delta.y) < .8D) {
            step = Math.min(step, Math.max(0D, separation - STOP_DISTANCE));
        }
        return distance < EPSILON ? new Vec3(0D, delta.y, 0D)
                : new Vec3(delta.x * step / distance, delta.y, delta.z * step / distance);
    }

    private boolean clearRoute(ClientLevel level, Vec3 target, boolean grounded) {
        if (grounded && Math.abs(target.y - position.y) > .3D) return false;
        int samples = Math.max(1, (int) Math.ceil(position.distanceTo(target) / .4D));
        for (int i = 1; i <= samples; i++) {
            Vec3 point = position.lerp(target, (double) i / samples);
            AABB body = box(point, grounded);
            if (!level.hasChunkAt(BlockPos.containing(point))
                    || level.getBlockCollisionsFromContext(COLLISION, body).iterator().hasNext()) return false;
            if (grounded && collide(level, body, new Vec3(0D, -.4D, 0D)).y <= -.4D + EPSILON) return false;
        }
        return true;
    }

    private Path findPath(ClientLevel level, Vec3 target, boolean grounded) {
        if (!level.hasChunkAt(BlockPos.containing(target))) return null;
        if (pathingMob == null) {
            // Моб нужен только для оценки габаритов и проходимости. В мир его не добавляем.
            pathingMob = grounded ? new Cat(EntityTypes.CAT, level) : new Bat(EntityTypes.BAT, level);
            var evaluator = grounded ? new WalkNodeEvaluator() : new FlyNodeEvaluator();
            evaluator.setCanPassDoors(true);
            pathFinder = new PathFinder(evaluator, 320);
        }
        Vec3 origin = position.add(0D, grounded ? 0D : FLIGHT_BOX_OFFSET, 0D);
        pathingMob.setPos(origin);
        pathingMob.setOnGround(grounded && collide(level, box(position, true), new Vec3(0D, -.08D, 0D)).y > -.08D + EPSILON);
        BlockPos start = BlockPos.containing(origin);
        var region = new PathNavigationRegion(level,
                start.offset(-PATH_RADIUS, -PATH_RADIUS, -PATH_RADIUS),
                start.offset(PATH_RADIUS, PATH_RADIUS, PATH_RADIUS));
        Path candidate = pathFinder.findPath(region, pathingMob,
                Set.of(BlockPos.containing(target.add(0D, grounded ? 0D : FLIGHT_BOX_OFFSET, 0D))), 24F, 0, 1F);
        if (candidate != null && candidate.getNodeCount() > 1) candidate.advance();
        return candidate;
    }

    private Vec3 walk(ClientLevel level, Vec3 requested) {
        AABB box = box(position, true);
        double fall = Math.max(-.65D, (verticalSpeed - .08D) * .98D);
        Vec3 motion = new Vec3(requested.x, fall, requested.z);
        Vec3 step = collide(level, box, motion);
        if (fall < 0D && step.y > fall + EPSILON && requested.horizontalDistanceSqr() > EPSILON
                && step.horizontalDistanceSqr() + EPSILON < requested.horizontalDistanceSqr()) {
            Vec3 up = collide(level, box, new Vec3(0D, 1.05D, 0D));
            if (up.y >= 1D && collide(level, box.move(0D, up.y, 0D),
                    new Vec3(requested.x, 0D, requested.z)).horizontalDistanceSqr() + EPSILON
                    >= requested.horizontalDistanceSqr()) {
                // Запрыгиваем на ступень, если над ней хватает места для питомца.
                fall = .45D;
                motion = new Vec3(requested.x, fall, requested.z);
                step = collide(level, box, motion);
            }
        }
        verticalSpeed = Math.abs(step.y - fall) > EPSILON ? 0D : fall;
        return step;
    }

    private Vec3 fly(ClientLevel level, Vec3 requested) {
        return collide(level, box(position, false), new Vec3(requested.x,
                Math.clamp(requested.y * .24D, -.18D, .18D), requested.z));
    }

    private static Vec3 spawn(ClientLevel level, Vec3 owner, GleamPet pet) {
        for (Vec3 offset : SPAWN_OFFSETS) {
            Vec3 point = owner.add(offset).add(0D, pet.grounded() ? .55D : FLIGHT_HEIGHT, 0D);
            if (!level.hasChunkAt(BlockPos.containing(point))) continue;
            AABB box = box(point, pet.grounded());
            if (level.getBlockCollisionsFromContext(COLLISION, box).iterator().hasNext()) continue;
            if (!pet.grounded()) return point;
            double fall = collide(level, box, new Vec3(0D, -1.75D, 0D)).y;
            if (fall > -1.75D + EPSILON) return point.add(0D, fall, 0D);
        }
        return null;
    }

    private static AABB box(Vec3 at, boolean grounded) {
        double radius = grounded ? .27D : .30D;
        double base = at.y + (grounded ? 0D : FLIGHT_BOX_OFFSET);
        return new AABB(at.x - radius, base, at.z - radius, at.x + radius, base + .54D, at.z + radius);
    }

    private static Vec3 collide(ClientLevel level, AABB box, Vec3 motion) {
        return Entity.collideBoundingBox(COLLISION, motion, box, level, List.of());
    }

    void reset() {
        path = null;
        pathingMob = null;
        pathFinder = null;
        direct = false;
        nextPathTick = 0;
        lastOwner = previous = position = null;
        horizontalSpeed = verticalSpeed = 0D;
        following = false;
        previousYaw = yaw = previousStride = stride = previousMovement = movement = 0F;
        age = stillTicks = 0;
    }

    Frame frame(float partial) {
        if (position == null) return null;
        long stationaryMillis = Math.round((stillTicks + partial) * 50D);
        long actionTime = stationaryMillis < 3_000L ? -1 : (stationaryMillis - 3_000L) % 8_000L;
        return new Frame(previous.lerp(position, partial), Mth.rotLerp(partial, previousYaw, yaw),
                Mth.lerp(partial, previousStride, stride), Mth.lerp(partial, previousMovement, movement),
                Math.round((age + partial) * 50D), actionTime);
    }
}
