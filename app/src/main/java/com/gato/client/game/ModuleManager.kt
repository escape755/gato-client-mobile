package com.gato.client.game


import android.content.Context
import android.net.Uri
import com.gato.client.application.AppContext
import com.gato.client.game.module.combat.AntiCrystalModule
import com.gato.client.game.module.combat.GatoAuraModule
import com.gato.client.game.module.combat.GatoAuraXModule
import com.gato.client.game.module.combat.Plus999AuraModule
import com.gato.client.game.module.combat.AntiKnockbackModule
import com.gato.client.game.module.combat.CrystalSmashModule
import com.gato.client.game.module.combat.HitAndRunModule
import com.gato.client.game.module.combat.HitboxModule
import com.gato.client.game.module.combat.KillauraModule
import com.gato.client.game.module.combat.TriggerBotModule
import com.gato.client.game.module.effect.AbsorptionModule
import com.gato.client.game.module.effect.BadOmenModule
import com.gato.client.game.module.effect.BlindnessModule
import com.gato.client.game.module.effect.ConduitPowerModule
import com.gato.client.game.module.effect.DarknessModule
import com.gato.client.game.module.effect.FatalPoisonModule
import com.gato.client.game.module.effect.FireResistanceModule
import com.gato.client.game.module.effect.HasteModule
import com.gato.client.game.module.effect.HealthBoostModule
import com.gato.client.game.module.effect.HungerModule
import com.gato.client.game.module.effect.InstantDamageModule
import com.gato.client.game.module.effect.InstantHealthModule
import com.gato.client.game.module.effect.InvisibilityModule
import com.gato.client.game.module.effect.JumpBoostModule
import com.gato.client.game.module.effect.LevitationModule
import com.gato.client.game.module.effect.MiningFatigueModule
import com.gato.client.game.module.effect.NauseaModule
import com.gato.client.game.module.effect.NightVisionModule
import com.gato.client.game.module.effect.PoisonModule
import com.gato.client.game.module.effect.PoseidonModule
import com.gato.client.game.module.effect.RegenerationModule
import com.gato.client.game.module.effect.ResistanceModule
import com.gato.client.game.module.effect.SaturationModule
import com.gato.client.game.module.effect.SlowFallingModule
import com.gato.client.game.module.effect.StrengthModule
import com.gato.client.game.module.effect.SwiftnessModule
import com.gato.client.game.module.effect.VillageHeroModule
import com.gato.client.game.module.effect.WeaknessModule
import com.gato.client.game.module.effect.WitherModule
import com.gato.client.game.module.misc.BaritoneModule
import com.gato.client.game.module.misc.CommandHandlerModule
import com.gato.client.game.module.misc.OffhandModule
import com.gato.client.game.module.misc.PopCounterModule
import com.gato.client.game.module.misc.DesyncModule
import com.gato.client.game.module.misc.TimerModule
import com.gato.client.game.module.misc.FakeDeathModule
import com.gato.client.game.module.misc.FakeXPModule
import com.gato.client.game.module.misc.NoChatModule
import com.gato.client.game.module.motion.NoClipModule
import com.gato.client.game.module.misc.PositionLoggerModule
import com.gato.client.game.module.misc.ReplayModule
import com.gato.client.game.module.visual.TimeShiftModule
import com.gato.client.game.module.visual.WeatherControllerModule
import com.gato.client.game.module.motion.AirJumpModule
import com.gato.client.game.module.motion.AntiAFKModule
import com.gato.client.game.module.motion.AutoWalkModule
import com.gato.client.game.module.motion.BhopModule
import com.gato.client.game.module.motion.BypassFlyModule
import com.gato.client.game.module.motion.FlyModule
import com.gato.client.game.module.motion.HighJumpModule
import com.gato.client.game.module.motion.JetPackModule
import com.gato.client.game.module.motion.MotionFlyModule
import com.gato.client.game.module.motion.SpeedModule
import com.gato.client.game.module.motion.SprintModule
import com.gato.client.game.module.particle.BreezeWindExplosionParticleModule
import com.gato.client.game.module.particle.BubbleParticleModule
import com.gato.client.game.module.particle.DustParticleModule
import com.gato.client.game.module.particle.ExplosionParticleModule
import com.gato.client.game.module.particle.EyeOfEnderDeathParticleModule
import com.gato.client.game.module.particle.FizzParticleModule
import com.gato.client.game.module.particle.HeartParticleModule
import com.gato.client.game.module.visual.CustomFovModule
import com.gato.client.game.module.visual.ESPModule
import com.gato.client.game.module.visual.FreeCameraModule
import com.gato.client.game.module.visual.NetworkInfoModule
import com.gato.client.game.module.visual.NoHurtCameraModule
import com.gato.client.game.module.visual.PositionDisplayModule
import com.gato.client.game.module.visual.SpeedDisplayModule
import com.gato.client.game.module.visual.WorldStateModule
import com.gato.client.game.module.visual.ZoomModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

object ModuleManager {

    private val _modules: MutableList<Module> = ArrayList()

    val modules: List<Module> = _modules

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        with(_modules) {
            add(FlyModule())
            add(BypassFlyModule())
            add(ESPModule())
            add(ZoomModule())
            add(CustomFovModule())
            add(AirJumpModule())
            add(NoClipModule())
            add(NightVisionModule())
            add(HasteModule())
            add(SpeedModule())
            add(JetPackModule())
            add(LevitationModule())
            add(HighJumpModule())
            add(SlowFallingModule())
            add(PoseidonModule())
            add(AntiKnockbackModule())
            add(RegenerationModule())
            add(BhopModule())
            add(SprintModule())
            add(NoHurtCameraModule())
            add(AutoWalkModule())
            add(AntiAFKModule())
            add(DesyncModule())
            add(PositionLoggerModule())
            add(PopCounterModule())
            add(OffhandModule())
            add(TimerModule())
            add(MotionFlyModule())
            add(FreeCameraModule())
            add(KillauraModule())
            add(GatoAuraModule())
            add(Plus999AuraModule())
            add(GatoAuraXModule())
            add(NauseaModule())
            add(HealthBoostModule())
            add(JumpBoostModule())
            add(ResistanceModule())
            add(FireResistanceModule())
            add(SwiftnessModule())
            add(InstantHealthModule())
            add(StrengthModule())
            add(InstantDamageModule())
            add(InvisibilityModule())
            add(SaturationModule())
            add(AbsorptionModule())
            add(BlindnessModule())
            add(AntiCrystalModule())
            add(HungerModule())
            add(WeaknessModule())
            add(PoisonModule())
            add(WitherModule())
            add(FatalPoisonModule())
            add(ConduitPowerModule())
            add(BadOmenModule())
            add(VillageHeroModule())
            add(DarknessModule())
            add(TimeShiftModule())
            add(WeatherControllerModule())
            add(FakeDeathModule())
            add(ExplosionParticleModule())
            add(BubbleParticleModule())
            add(HeartParticleModule())
            add(FakeXPModule())
            add(DustParticleModule())
            add(EyeOfEnderDeathParticleModule())
            add(FizzParticleModule())
            add(BreezeWindExplosionParticleModule())
            add(HitAndRunModule())
            add(HitboxModule())
            add(CrystalSmashModule())
            add(TriggerBotModule())
            add(NoChatModule())
            add(SpeedDisplayModule())
            add(PositionDisplayModule())
            add(CommandHandlerModule())
            add(NetworkInfoModule())
            add(MiningFatigueModule())
            add(WorldStateModule())
            add(ReplayModule())
            add(BaritoneModule())
        }
    }

    fun saveConfig() {
        val configsDir = AppContext.instance.filesDir.resolve("configs")
        configsDir.mkdirs()

        val config = configsDir.resolve("UserConfig.json")
        val jsonObject = buildJsonObject {
            put("modules", buildJsonObject {
                _modules.forEach {
                    if (it.private) {
                        return@forEach
                    }
                    put(it.name, it.toJson())
                }
            })
        }

        config.writeText(json.encodeToString(jsonObject))
    }

    fun loadConfig() {
        val configsDir = AppContext.instance.filesDir.resolve("configs")
        configsDir.mkdirs()

        val config = configsDir.resolve("UserConfig.json")
        if (!config.exists()) {
            return
        }

        val jsonString = config.readText()
        if (jsonString.isEmpty()) {
            return
        }

        val jsonObject = json.parseToJsonElement(jsonString).jsonObject
        val modules = jsonObject["modules"]!!.jsonObject
        _modules.forEach { module ->
            (modules[module.name] as? JsonObject)?.let {
                module.fromJson(it)
            }
        }
    }

    fun exportConfig(): String {
        val jsonObject = buildJsonObject {
            put("modules", buildJsonObject {
                _modules.forEach {
                    if (it.private) {
                        return@forEach
                    }
                    put(it.name, it.toJson())
                }
            })
        }
        return json.encodeToString(jsonObject)
    }

    fun importConfig(configStr: String) {
        try {
            val jsonObject = json.parseToJsonElement(configStr).jsonObject
            val modules = jsonObject["modules"]?.jsonObject ?: return

            _modules.forEach { module ->
                modules[module.name]?.let {
                    if (it is JsonObject) {
                        module.fromJson(it)
                    }
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid config format")
        }
    }

    fun exportConfigToFile(context: Context, fileName: String): Boolean {
        return try {
            val configsDir = context.getExternalFilesDir("configs")
            configsDir?.mkdirs()

            val configFile = File(configsDir, "$fileName.json")
            configFile.writeText(exportConfig())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importConfigFromFile(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val configStr = input.bufferedReader().readText()
                importConfig(configStr)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

}