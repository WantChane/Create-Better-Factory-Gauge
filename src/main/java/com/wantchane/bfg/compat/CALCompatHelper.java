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
	private static Class<?> iplClass;
	private static Method getPromiseLimit;
	private static Method setPromiseLimit;
	private static Method hasPromiseLimit;
	private static Method getAdditionalStock;
	private static Method setAdditionalStock;
	private static Method hasAdditionalStock;

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
			iplClass = Class.forName(IPL_CLASS);
			getPromiseLimit = iplClass.getMethod("getCALPromiseLimit");
			setPromiseLimit = iplClass.getMethod("setCALPromiseLimit", int.class);
			hasPromiseLimit = iplClass.getMethod("hasCALPromiseLimit");
			getAdditionalStock = iplClass.getMethod("getCALAdditionalStock");
			setAdditionalStock = iplClass.getMethod("setCALAdditionalStock", int.class);
			hasAdditionalStock = iplClass.getMethod("hasCALAdditionalStock");

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

	public static boolean isPromiseLimit(FactoryPanelBehaviour behaviour) {
		if (!isLoaded())
			return false;
		return iplClass.isInstance(behaviour);
	}

	public static boolean hasPromiseLimit(FactoryPanelBehaviour behaviour) {
		if (!isLoaded())
			return false;
		try {
			return (boolean) hasPromiseLimit.invoke(behaviour);
		} catch (Exception e) {
			return false;
		}
	}

	public static int getPromiseLimit(FactoryPanelBehaviour behaviour) {
		try {
			return (int) getPromiseLimit.invoke(behaviour);
		} catch (Exception e) {
			return -1;
		}
	}

	public static void setPromiseLimit(FactoryPanelBehaviour behaviour, int value) {
		try {
			setPromiseLimit.invoke(behaviour, value);
		} catch (Exception ignored) {
		}
	}

	public static int getAdditionalStock(FactoryPanelBehaviour behaviour) {
		try {
			return (int) getAdditionalStock.invoke(behaviour);
		} catch (Exception e) {
			return 0;
		}
	}

	public static void setAdditionalStock(FactoryPanelBehaviour behaviour, int value) {
		try {
			setAdditionalStock.invoke(behaviour, value);
		} catch (Exception ignored) {
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
