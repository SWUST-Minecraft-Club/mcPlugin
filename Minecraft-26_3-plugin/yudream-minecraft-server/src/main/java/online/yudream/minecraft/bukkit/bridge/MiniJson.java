package online.yudream.minecraft.bukkit.bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MiniJson {
   private MiniJson() {
   }

   public static Object parse(String text) {
      if (text == null) {
         throw new IllegalArgumentException("JSON text is null");
      } else {
         Parser parser = new Parser(text);
         Object value = parser.readValue();
         parser.skipWhitespace();
         if (!parser.atEnd()) {
            throw new IllegalArgumentException("Unexpected trailing content at index " + parser.index);
         } else {
            return value;
         }
      }
   }

   public static Map<String, Object> parseObject(String text) {
      try {
         Object value = parse(text);
         return value instanceof Map ? (Map)value : null;
      } catch (IllegalArgumentException var2) {
         return null;
      }
   }

   public static String string(Map<String, Object> object, String key, String fallback) {
      Object value = object == null ? null : object.get(key);
      return value instanceof String ? (String)value : fallback;
   }

   public static int integer(Map<String, Object> object, String key, int fallback) {
      Object value = object == null ? null : object.get(key);
      return value instanceof Number ? ((Number)value).intValue() : fallback;
   }

   public static long longValue(Map<String, Object> object, String key, long fallback) {
      Object value = object == null ? null : object.get(key);
      return value instanceof Number ? ((Number)value).longValue() : fallback;
   }

   public static boolean bool(Map<String, Object> object, String key, boolean fallback) {
      Object value = object == null ? null : object.get(key);
      return value instanceof Boolean ? (Boolean)value : fallback;
   }

   private static final class Parser {
      private final String text;
      private int index;

      private Parser(String text) {
         this.text = text;
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
            throw new IllegalArgumentException("Unexpected end of JSON input");
         } else {
            return this.text.charAt(this.index);
         }
      }

      private void expect(char expected) {
         if (!this.atEnd() && this.text.charAt(this.index) == expected) {
            ++this.index;
         } else {
            throw new IllegalArgumentException("Expected '" + expected + "' at index " + this.index);
         }
      }

      private Object readValue() {
         this.skipWhitespace();
         char c = this.peek();
         switch (c) {
            case '"':
               return this.readString();
            case '[':
               return this.readArray();
            case 'f':
               this.readLiteral("false");
               return Boolean.FALSE;
            case 'n':
               this.readLiteral("null");
               return null;
            case 't':
               this.readLiteral("true");
               return Boolean.TRUE;
            case '{':
               return this.readObject();
            default:
               return this.readNumber();
         }
      }

      private Map<String, Object> readObject() {
         this.expect('{');
         Map<String, Object> result = new LinkedHashMap();
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
                     throw new IllegalArgumentException("Expected ',' or '}' at index " + this.index);
                  }
               }

               ++this.index;
            }
         }
      }

      private List<Object> readArray() {
         this.expect('[');
         List<Object> result = new ArrayList();
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
                     throw new IllegalArgumentException("Expected ',' or ']' at index " + this.index);
                  }
               }

               ++this.index;
            }
         }
      }

      private void readLiteral(String literal) {
         if (!this.text.startsWith(literal, this.index)) {
            throw new IllegalArgumentException("Expected '" + literal + "' at index " + this.index);
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
                  throw new IllegalArgumentException("Unterminated escape sequence");
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
                        throw new IllegalArgumentException("Truncated \\u escape at index " + this.index);
                     }

                     String hex = this.text.substring(this.index, this.index + 4);

                     try {
                        out.append((char)Integer.parseInt(hex, 16));
                     } catch (NumberFormatException var6) {
                        throw new IllegalArgumentException("Invalid \\u escape '" + hex + "' at index " + this.index);
                     }

                     this.index += 4;
                     break;
                  default:
                     throw new IllegalArgumentException("Unsupported escape '\\" + escape + "' at index " + (this.index - 1));
               }
            }
         }

         throw new IllegalArgumentException("Unterminated string starting before index " + this.index);
      }

      private Object readNumber() {
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
            throw new IllegalArgumentException("Expected a value at index " + start);
         } else {
            String literal = this.text.substring(start, this.index);
            if (integral) {
               try {
                  return Long.parseLong(literal);
               } catch (NumberFormatException var6) {
               }
            }

            try {
               return Double.parseDouble(literal);
            } catch (NumberFormatException var5) {
               throw new IllegalArgumentException("Invalid number '" + literal + "' at index " + start);
            }
         }
      }
   }
}
