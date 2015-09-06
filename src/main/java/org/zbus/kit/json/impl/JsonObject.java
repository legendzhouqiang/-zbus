package org.zbus.kit.json.impl;

import java.util.HashMap;

import org.zbus.kit.json.Json;

public class JsonObject extends HashMap<String, Object> {  
	private static final long serialVersionUID = -3007199945096476930L;
	
	public Integer getInt(String key) {
		return CastKit.intValue(get(key));
	}
	public Long getLong(String key) {
		return CastKit.longValue(key);
	}
	public Short getShort(String key) {
		return CastKit.shortValue(get(key));
	} 
	public Float getFloat(String key) {
		return CastKit.floatValue(get(key));
	} 
	public Double getDouble(String key) {
		return CastKit.doubleValue(get(key));
	} 
	public Byte getByte(String key) {
		return CastKit.byteValue(get(key));
	} 
	public Boolean getBool(String key) {
		return CastKit.booleanValue(get(key));
	}
	public Character getChar(String key) {
		return CastKit.charValue(get(key));
	}
	public String getString(String key) {
		return CastKit.stringValue(get(key));
	}
	public <T> T getBean(String key, Class<T> clazz) {
		return CastKit.objectValue(getJsonObject(key), clazz);
	}

	public JsonObject getJsonObject(String key) {
		Object obj = get(key);
		return obj instanceof JsonObject ? JsonObject.class.cast(obj) : null;
	}

	public JsonArray getJsonArray(String key) {
		Object obj = get(key);
		return obj instanceof JsonArray ? JsonArray.class.cast(obj) : null;
	}

	public <T> T toBean(Class<T> clazz) {
		return CastKit.objectValue(this, clazz);
	}
	
	public String toJsonString() {
		return Json.toJson(this);
	}

	public String toString() {
		return toJsonString();
	}
}