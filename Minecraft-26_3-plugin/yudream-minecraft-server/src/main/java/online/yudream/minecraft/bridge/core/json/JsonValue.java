package online.yudream.minecraft.bridge.core.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonValue {
   private static final JsonValue NULL;
   private final Kind kind;
   private final Object raw;

   private JsonValue(Kind kind, Object raw) {
      this.kind = kind;
      this.raw = raw;
   }

   public static JsonValue object() {
      return new JsonValue(JsonValue.Kind.OBJECT, new LinkedHashMap());
   }

   public static JsonValue array() {
      return new JsonValue(JsonValue.Kind.ARRAY, new ArrayList());
   }

   public static JsonValue of(String value) {
      return value == null ? NULL : new JsonValue(JsonValue.Kind.STRING, value);
   }

   public static JsonValue of(boolean value) {
      return new JsonValue(JsonValue.Kind.BOOLEAN, value);
   }

   public static JsonValue of(long value) {
      return new JsonValue(JsonValue.Kind.NUMBER, value);
   }

   public static JsonValue of(double value) {
      return new JsonValue(JsonValue.Kind.NUMBER, value);
   }

   public static JsonValue nul() {
      return NULL;
   }

   public Kind kind() {
      return this.kind;
   }

   public boolean isObject() {
      return this.kind == JsonValue.Kind.OBJECT;
   }

   public boolean isArray() {
      return this.kind == JsonValue.Kind.ARRAY;
   }

   public boolean isNull() {
      return this.kind == JsonValue.Kind.NULL;
   }

   public boolean isNumber() {
      return this.kind == JsonValue.Kind.NUMBER;
   }

   public boolean isString() {
      return this.kind == JsonValue.Kind.STRING;
   }

   public JsonValue put(String key, JsonValue value) {
      this.require(JsonValue.Kind.OBJECT, "put");
      ((Map)this.raw).put(key, value == null ? NULL : value);
      return this;
   }

   public JsonValue put(String key, String value) {
      return this.put(key, of(value));
   }

   public JsonValue put(String key, long value) {
      return this.put(key, of(value));
   }

   public JsonValue put(String key, boolean value) {
      return this.put(key, of(value));
   }

   public JsonValue add(JsonValue value) {
      this.require(JsonValue.Kind.ARRAY, "add");
      ((List)this.raw).add(value == null ? NULL : value);
      return this;
   }

   public JsonValue get(String key) {
      return this.kind != JsonValue.Kind.OBJECT ? null : (JsonValue)((Map)this.raw).get(key);
   }

   public boolean has(String key) {
      JsonValue value = this.get(key);
      return value != null && !value.isNull();
   }

   public JsonValue getOr(String key, JsonValue fallback) {
      JsonValue value = this.get(key);
      return value != null && !value.isNull() ? value : fallback;
   }

   public Map<String, JsonValue> members() {
      return this.kind != JsonValue.Kind.OBJECT ? Collections.emptyMap() : Collections.unmodifiableMap((Map)this.raw);
   }

   public List<JsonValue> items() {
      return this.kind != JsonValue.Kind.ARRAY ? Collections.emptyList() : Collections.unmodifiableList((List)this.raw);
   }

   public String asString(String fallback) {
      return this.kind == JsonValue.Kind.STRING ? (String)this.raw : fallback;
   }

   public boolean asBoolean(boolean fallback) {
      return this.kind != JsonValue.Kind.BOOLEAN ? fallback : (Boolean)this.raw;
   }

   public long asLong(long fallback) {
      if (this.kind == JsonValue.Kind.NUMBER) {
         return ((Number)this.raw).longValue();
      } else if (this.kind == JsonValue.Kind.STRING) {
         try {
            return Long.parseLong(((String)this.raw).trim());
         } catch (NumberFormatException var4) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   public int asInt(int fallback) {
      if (this.kind == JsonValue.Kind.NUMBER) {
         return ((Number)this.raw).intValue();
      } else if (this.kind == JsonValue.Kind.STRING) {
         try {
            return Integer.parseInt(((String)this.raw).trim());
         } catch (NumberFormatException var3) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   public double asDouble(double fallback) {
      if (this.kind == JsonValue.Kind.NUMBER) {
         return ((Number)this.raw).doubleValue();
      } else if (this.kind == JsonValue.Kind.STRING) {
         try {
            return Double.parseDouble(((String)this.raw).trim());
         } catch (NumberFormatException var4) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private void require(Kind expected, String operation) {
      if (this.kind != expected) {
         throw new IllegalStateException(operation + " needs a " + expected + " but this value is " + this.kind);
      }
   }

   public String toString() {
      StringBuilder out = new StringBuilder();
      this.writeTo(out);
      return out.toString();
   }

   private void writeTo(StringBuilder out) {
      switch (this.kind.ordinal()) {
         case 0:
            out.append('{');
            boolean first = true;

            for(Map.Entry<String, JsonValue> entry : this.members().entrySet()) {
               if (!first) {
                  out.append(',');
               }

               first = false;
               writeString(out, (String)entry.getKey());
               out.append(':');
               ((JsonValue)entry.getValue()).writeTo(out);
            }

            out.append('}');
            break;
         case 1:
            out.append('[');
            boolean firstItem = true;

            for(JsonValue item : this.items()) {
               if (!firstItem) {
                  out.append(',');
               }

               firstItem = false;
               item.writeTo(out);
            }

            out.append(']');
            break;
         case 2:
            writeString(out, (String)this.raw);
            break;
         case 3:
            if (this.raw instanceof Long) {
               out.append((Long)this.raw);
            } else {
               double value = (Double)this.raw;
               if (value == Math.rint(value) && !Double.isInfinite(value) && value >= (double)Long.MIN_VALUE && value <= (double)Long.MAX_VALUE) {
                  out.append((long)value);
               } else {
                  out.append(value);
               }
            }
            break;
         case 4:
            out.append((Boolean)this.raw ? "true" : "false");
            break;
         default:
            out.append("null");
      }

   }

   private static void writeString(StringBuilder out, String value) {
      out.append('"');

      for(int i = 0; i < value.length(); ++i) {
         char c = value.charAt(i);
         switch (c) {
            case '\b':
               out.append("\\b");
               break;
            case '\t':
               out.append("\\t");
               break;
            case '\n':
               out.append("\\n");
               break;
            case '\f':
               out.append("\\f");
               break;
            case '\r':
               out.append("\\r");
               break;
            case '"':
               out.append("\\\"");
               break;
            case '\\':
               out.append("\\\\");
               break;
            default:
               if (c < ' ') {
                  out.append(String.format("\\u%04x", Integer.valueOf(c)));
               } else {
                  out.append(c);
               }
         }
      }

      out.append('"');
   }

   public static JsonValue parse(String text) {
      if (text == null) {
         throw new JsonSyntaxException("JSON text is null");
      } else {
         Parser parser = new Parser(text);
         JsonValue value = parser.readValue();
         parser.skipWhitespace();
         if (!parser.atEnd()) {
            throw new JsonSyntaxException("Unexpected trailing content at index " + parser.index());
         } else {
            return value;
         }
      }
   }

   public static JsonValue tryParse(String text) {
      try {
         return parse(text);
      } catch (JsonSyntaxException var2) {
         return null;
      }
   }

   static {
      NULL = new JsonValue(JsonValue.Kind.NULL, (Object)null);
   }

   public static enum Kind {
      OBJECT,
      ARRAY,
      STRING,
      NUMBER,
      BOOLEAN,
      NULL;

      // $FF: synthetic method
      private static Kind[] $values() {
         return new Kind[]{OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL};
      }
   }

   private static final class Parser {
      private final String text;
      private int index;

      private Parser(String text) {
         this.text = text;
      }

      private int index() {
         return this.index;
      }

      private boolean atEnd() {
         return this.index >= this.text.length();
      }

      private void skipWhitespace() {
         while(true) {
            if (this.index < this.text.length()) {
               char c = this.text.charAt(this.index);
               if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                  ++this.index;
                  continue;
               }
            }

            return;
         }
      }

      private char peek() {
         if (this.atEnd()) {
            throw new JsonSyntaxException("Unexpected end of JSON input");
         } else {
            return this.text.charAt(this.index);
         }
      }

      private void expect(char expected) {
         if (!this.atEnd() && this.text.charAt(this.index) == expected) {
            ++this.index;
         } else {
            throw new JsonSyntaxException("Expected '" + expected + "' at index " + this.index);
         }
      }

      private JsonValue readValue() {
         this.skipWhitespace();
         char c = this.peek();
         switch (c) {
            case '"':
               return JsonValue.of(this.readString());
            case '[':
               return this.readArray();
            case 'f':
               this.readLiteral("false");
               return JsonValue.of(false);
            case 'n':
               this.readLiteral("null");
               return JsonValue.nul();
            case 't':
               this.readLiteral("true");
               return JsonValue.of(true);
            case '{':
               return this.readObject();
            default:
               return this.readNumber();
         }
      }

      private JsonValue readObject() {
         this.expect('{');
         JsonValue result = JsonValue.object();
         this.skipWhitespace();
         if (!this.atEnd() && this.peek() == '}') {
            ++this.index;
            return result;
         } else {
            while(true) {
               this.skipWhitespace();
               String key = this.readString();
               this.skipWhitespace();
               this.expect(':');
               result.put(key, this.readValue());
               this.skipWhitespace();
               char next = this.peek();
               if (next != ',') {
                  if (next == '}') {
                     ++this.index;
                     return result;
                  } else {
                     throw new JsonSyntaxException("Expected ',' or '}' at index " + this.index);
                  }
               }

               ++this.index;
            }
         }
      }

      private JsonValue readArray() {
         this.expect('[');
         JsonValue result = JsonValue.array();
         this.skipWhitespace();
         if (!this.atEnd() && this.peek() == ']') {
            ++this.index;
            return result;
         } else {
            while(true) {
               result.add(this.readValue());
               this.skipWhitespace();
               char next = this.peek();
               if (next != ',') {
                  if (next == ']') {
                     ++this.index;
                     return result;
                  } else {
                     throw new JsonSyntaxException("Expected ',' or ']' at index " + this.index);
                  }
               }

               ++this.index;
            }
         }
      }

      private void readLiteral(String literal) {
         if (!this.text.startsWith(literal, this.index)) {
            throw new JsonSyntaxException("Expected '" + literal + "' at index " + this.index);
         } else {
            this.index += literal.length();
         }
      }

      private String readString() {
         this.expect('"');
         StringBuilder out = new StringBuilder();

         while(!this.atEnd()) {
            char c = this.text.charAt(this.index++);
            if (c == '"') {
               return out.toString();
            }

            if (c != '\\') {
               out.append(c);
            } else {
               if (this.atEnd()) {
                  throw new JsonSyntaxException("Unterminated escape sequence");
               }

               char escape = this.text.charAt(this.index++);
               switch (escape) {
                  case '"':
                     out.append('"');
                     break;
                  case '/':
                     out.append('/');
                     break;
                  case '\\':
                     out.append('\\');
                     break;
                  case 'b':
                     out.append('\b');
                     break;
                  case 'f':
                     out.append('\f');
                     break;
                  case 'n':
                     out.append('\n');
                     break;
                  case 'r':
                     out.append('\r');
                     break;
                  case 't':
                     out.append('\t');
                     break;
                  case 'u':
                     if (this.index + 4 > this.text.length()) {
                        throw new JsonSyntaxException("Truncated \\u escape at index " + this.index);
                     }

                     String hex = this.text.substring(this.index, this.index + 4);

                     try {
                        out.append((char)Integer.parseInt(hex, 16));
                     } catch (NumberFormatException var6) {
                        throw new JsonSyntaxException("Invalid \\u escape '" + hex + "' at index " + this.index);
                     }

                     this.index += 4;
                     break;
                  default:
                     throw new JsonSyntaxException("Unsupported escape '\\" + escape + "' at index " + (this.index - 1));
               }
            }
         }

         throw new JsonSyntaxException("Unterminated string starting before index " + this.index);
      }

      private JsonValue readNumber() {
         int start = this.index;
         if (!this.atEnd() && (this.peek() == '-' || this.peek() == '+')) {
            ++this.index;
         }

         boolean integral = true;

         while(!this.atEnd()) {
            char c = this.text.charAt(this.index);
            if (c >= '0' && c <= '9') {
               ++this.index;
            } else {
               if (c != '.' && c != 'e' && c != 'E' && c != '+' && c != '-') {
                  break;
               }

               integral = false;
               ++this.index;
            }
         }

         if (start == this.index) {
            throw new JsonSyntaxException("Expected a value at index " + start);
         } else {
            String literal = this.text.substring(start, this.index);
            if (integral) {
               try {
                  return JsonValue.of(Long.parseLong(literal));
               } catch (NumberFormatException var6) {
               }
            }

            try {
               return JsonValue.of(Double.parseDouble(literal));
            } catch (NumberFormatException var5) {
               throw new JsonSyntaxException("Invalid number '" + literal + "' at index " + start);
            }
         }
      }
   }
}
