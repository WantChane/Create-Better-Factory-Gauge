package com.wantchane.bfg.compat;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CALCompatHelper {

	private static final String MOD_ID = "createadditionallogistics";
	private static final String IPL_CLASS = "dev.khloeleclair.create.additionallogistics.common.utilities.IPromiseLimit";
	private static final String CONFIG_CLASS = "dev.khloeleclair.create.additionallogistics.common.Config";
	private static final String PACKET_CLASS = "dev.khloeleclair.create.additionallogistics.common.registries.CALPackets$UpdateGaugePromiseLimit";

	private static boolean checked;
	private static boolean loaded;

	// IPromiseLimit
	private static Method getPromiseLimit;
	private static Method getAdditionalStock;

	// Config
	private static Object configCommon;
	private static Field enablePromiseLimitsField;
	private static Field enableAdditionalStockField;
	private static Method booleanValueGet;

	// Packet
	private static Constructor<?> updatePacketCtor;
	private static Method updatePacketSend;

	private static void ensureInit() {
		if (checked)
			return;
		checked = true;
		loaded = ModList.get().isLoaded(MOD_ID);
		if (!loaded)
			return;

		try {
			Class<?> iplClass = Class.forName(IPL_CLASS);
			getPromiseLimit = iplClass.getMethod("getCALPromiseLimit");
			getAdditionalStock = iplClass.getMethod("getCALAdditionalStock");

			Class<?> configClass = Class.forName(CONFIG_CLASS);
			Field commonField = configClass.getField("Common");
			configCommon = commonField.get(null);
			enablePromiseLimitsField = configCommon.getClass().getField("enablePromiseLimits");
			enableAdditionalStockField = configCommon.getClass().getField("enableAdditionalStock");
			Object bv = enablePromiseLimitsField.get(configCommon);
			booleanValueGet = bv.getClass().getMethod("get");

			Class<?> packetClass = Class.forName(PACKET_CLASS);
			updatePacketCtor = packetClass.getConstructor(FactoryPanelPosition.class, int.class, int.class);
			updatePacketSend = packetClass.getMethod("send");
		} catch (Exception e) {
			loaded = false;
		}
	}

	public static boolean isLoaded() {
		ensureInit();
		return loaded;
	}

	public static int getVerticalGap() {
		return isLoaded() ? 22 : 0;
	}

	public static int getPromiseLimit(FactoryPanelBehaviour behaviour) {
		try {
			return (int) getPromiseLimit.invoke(behaviour);
		} catch (Exception e) {
			return -1;
		}
	}

	public static int getAdditionalStock(FactoryPanelBehaviour behaviour) {
		try {
			return (int) getAdditionalStock.invoke(behaviour);
		} catch (Exception e) {
			return 0;
		}
	}

	public static boolean isPromiseLimitsEnabled() {
		try {
			return (boolean) booleanValueGet.invoke(enablePromiseLimitsField.get(configCommon));
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean isAdditionalStockEnabled() {
		try {
			return (boolean) booleanValueGet.invoke(enableAdditionalStockField.get(configCommon));
		} catch (Exception e) {
			return false;
		}
	}

	public static void sendUpdatePacket(FactoryPanelPosition position, int limit, int additional) {
		if (!isLoaded())
			return;
		try {
			Object packet = updatePacketCtor.newInstance(position, limit, additional);
			updatePacketSend.invoke(packet);
		} catch (Exception ignored) {
		}
	}
}
