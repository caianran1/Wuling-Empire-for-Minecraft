package com.wuling.empire.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 腐肉武灵召唤出来的僵尸随从（2026-10-02 用户设定）。
 *
 * <p>它<b>不是</b>原版僵尸的换皮 —— 行为完全重写：
 * <ul>
 *   <li><b>认主</b>：记住召唤它的玩家，绝不攻击主人，也不攻击同类召唤物；</li>
 *   <li><b>只打敌对生物</b>：目标只从 {@link Enemy} 里挑（野生的原版僵尸也算）；</li>
 *   <li><b>跟随</b>：没打架的时候跟着主人跑，远了就追；</li>
 *   <li><b>不烧身、不消失</b>：白天不烧、不因远离玩家被回收、被清除；</li>
 *   <li><b>可飞行</b>：大境界达到设定档位（默认钻石）后悬空跟随与追击；</li>
 *   <li><b>手动解散</b>：主人按 Shift+P 让它消散（见 {@code WuLingDismissPacket}）。</li>
 * </ul>
 *
 * <p>之所以直接继承 {@link Zombie} 而不是从 {@link Mob} 从零搭：
 * 这样能白拿原版僵尸的模型、贴图、动画与音效（渲染器直接复用
 * {@code ZombieRenderer}），我们只负责改行为。
 */
public class WuLingZombieEntity extends Zombie {

    private static final String KEY_OWNER = "WuLingOwner";
    private static final String KEY_FLYING = "WuLingFlying";

    /**
     * 飞行状态要走同步数据 —— 客户端的 {@code travel} 也要据此决定受不受重力，
     * 只写在服务端 NBT 里客户端会一边悬停一边往下飘。
     */
    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(WuLingZombieEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private UUID ownerUUID;

    public WuLingZombieEntity(EntityType<? extends WuLingZombieEntity> type, Level level) {
        super(type, level);
        this.setCanBreakDoors(false);
        // 召唤物不受「远离玩家就消失」的规则约束
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes();
    }

    // ===================== 主人 =====================

    public void setOwner(@Nullable Player player) {
        this.ownerUUID = player == null ? null : player.getUUID();
    }

    @Nullable
    public UUID ownerUUID() {
        return ownerUUID;
    }

    /** 主人（只在服务端取得到 —— 客户端拿不到 PlayerList） */
    @Nullable
    public Player getOwner() {
        if (ownerUUID == null || !(this.level() instanceof ServerLevel server)) {
            return null;
        }
        return server.getServer().getPlayerList().getPlayer(ownerUUID);
    }

    public boolean isOwner(Entity entity) {
        return ownerUUID != null && ownerUUID.equals(entity.getUUID());
    }

    // ===================== 飞行 =====================

    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    public void setFlying(boolean flying) {
        this.entityData.set(DATA_FLYING, flying);
        this.setNoGravity(flying);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLYING, false);
    }

    // ===================== AI =====================

    @Override
    protected void registerGoals() {
        // 刻意不调 super —— 父类那套「打玩家 / 打村民 / 打铁傀儡」的目标选择器
        // 正是我们要去掉的，留着会反过来咬主人。
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class,
                5, false, false, this::isValidTarget));
    }

    /** 只有「敌对生物、且不是主人 / 不是同类召唤物」才配当目标 */
    private boolean isValidTarget(LivingEntity target) {
        return target != this
                && !isOwner(target)
                && !(target instanceof WuLingZombieEntity)
                && target instanceof Enemy;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isOwner(target) && !(target instanceof WuLingZombieEntity) && super.canAttack(target);
    }

    /**
     * 兜住所有设目标的入口。
     *
     * <p>父类僵尸在「被打到」和「召唤援军」时都会自己 {@code setTarget(...)}，
     * 那两处不受我们目标选择器的约束 —— 玩家不小心砍到自己的僵尸、
     * 或者僵尸被打时想拉援军，都会绕过 {@link #isValidTarget}。
     * 在这一层拦一道，非法目标一律拒绝。
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && !isValidTarget(target)) {
            return;
        }
        super.setTarget(target);
    }

    /**
     * 跟随主人。
     *
     * <p>打架时不跟（让位给攻击目标），脱离战斗且距离超过 10 格才追，
     * 追到 6 格以内就停 —— 免得在主人脚边来回蹭。
     */
    private static final class FollowOwnerGoal extends Goal {

        private final WuLingZombieEntity zombie;
        private final double speed;
        @Nullable
        private LivingEntity owner;

        FollowOwnerGoal(WuLingZombieEntity zombie, double speed) {
            this.zombie = zombie;
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            this.owner = zombie.getOwner();
            if (owner == null || !owner.isAlive() || owner.isSpectator()) {
                return false;
            }
            if (zombie.getTarget() != null) {
                return false;
            }
            return zombie.distanceToSqr(owner) > 100.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return zombie.getTarget() == null
                    && owner != null && owner.isAlive()
                    && zombie.distanceToSqr(owner) > 36.0D;
        }

        @Override
        public void start() {
            if (owner != null && !zombie.isFlying()) {
                zombie.getNavigation().moveTo(owner, speed);
            }
        }

        @Override
        public void tick() {
            if (owner == null) {
                return;
            }
            if (zombie.isFlying()) {
                // 飞行时不走地面寻路，直接朝主人推速度（见 travel）
                zombie.flyTowards(owner);
            } else if (zombie.tickCount % 10 == 0) {
                zombie.getNavigation().moveTo(owner, speed);
            }
        }

        @Override
        public void stop() {
            this.owner = null;
            zombie.getNavigation().stop();
        }
    }

    /** 朝目标做 3D 移动；距离越远推得越快，贴身后减速避免糊脸 */
    private void flyTowards(LivingEntity target) {
        Vec3 want = target.position()
                .add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                .subtract(this.position());
        double dist = want.length();
        if (dist < 1.0E-4D) {
            return;
        }
        double speed = Math.min(0.35D, 0.05D + dist * 0.03D);
        this.setDeltaMovement(want.normalize().scale(speed));
    }

    // ===================== 移动 =====================

    @Override
    public void travel(Vec3 input) {
        if (this.isFlying()) {
            if (this.isEffectiveAi()) {
                // 飞行 = 忽略地面寻路与重力，自己推自己；客户端不推进位置，
                // 交给服务端同步，否则两边各算一次会抖动。
                //
                // ⚠️ 战斗时也必须自己推：FollowOwnerGoal 一旦有目标就退出，
                // 而它才是唯一的速度来源 —— 不管的话飞行随从打架时会原地悬停。
                LivingEntity target = this.getTarget();
                if (target != null && target.isAlive()) {
                    this.flyTowards(target);
                }
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.91D));
            }
            return;
        }
        super.travel(input);
    }

    // ===================== 生存性覆写 =====================

    /** 召唤物白天不烧 */
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public boolean canBreakDoors() {
        return false;
    }

    /** 不因离玩家太远被回收 */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** 难度 / 距离都不清除它 —— 只认主人的解散指令 */
    @Override
    public void checkDespawn() {
    }

    /** 被玩家打死不掉腐肉铁锭这些原版战利品 */
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
    }

    /** 打死它不给经验（{@code Zombie} 已把本方法提为 public，必须同修饰符才能覆写） */
    @Override
    public int getExperienceReward() {
        return 0;
    }

    // ===================== 存档 =====================

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(KEY_OWNER)) {
            this.ownerUUID = tag.getUUID(KEY_OWNER);
        }
        this.setFlying(tag.getBoolean(KEY_FLYING));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null) {
            tag.putUUID(KEY_OWNER, ownerUUID);
        }
        tag.putBoolean(KEY_FLYING, this.isFlying());
    }
}
