package org.zbus.common.json.serializer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Calendar;
import java.util.Date;

import org.zbus.common.json.parser.DefaultJSONParser;
import org.zbus.common.json.parser.JSONToken;
import org.zbus.common.json.parser.deserializer.DateDeserializer;
import org.zbus.common.json.parser.deserializer.ObjectDeserializer;

public class CalendarCodec implements ObjectSerializer, ObjectDeserializer {

    public final static CalendarCodec instance = new CalendarCodec();

    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType) throws IOException {
        Calendar calendar = (Calendar) object;
        Date date = calendar.getTime();
        serializer.write(date);
    }

    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
        Date date = DateDeserializer.instance.deserialze(parser, type, fieldName);
        
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        
        return (T) calendar;
    }

    public int getFastMatchToken() {
        return JSONToken.LITERAL_INT;
    }
}
