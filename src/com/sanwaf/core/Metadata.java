package com.sanwaf.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class Metadata {
  static final String XML_METADATA = "metadata";
  static final String XML_SECURED = "secured";
  static final String XML_PARAMETERS = "parameters";
  static final String XML_HEADERS = "headers";
  static final String XML_COOKIES = "cookies";
  static final String INDEX_PARM_MARKER = "  ";
  static final String STAR = "*";

  private static final String[] CHAR_STRINGS = new String[128];
  static {
    for (int i = 0; i < 128; i++) {
      CHAR_STRINGS[i] = String.valueOf((char) i);
    }
  }

  com.sanwaf.log.Logger logger;
  boolean enabled = false;
  boolean caseSensitive = true;
  boolean endpointIsStrict = false;
  boolean endpointIsStrictAllowLess = false;
  Modes endpointMode = Modes.BLOCK;
  Map<String, Item> items = new HashMap<>();
  Map<String, Set<String>> index = new HashMap<>();
  Shield shield;

  Metadata(Shield shield, Xml xml, String type, com.sanwaf.log.Logger logger, boolean isDetect) {
    this.logger = logger;
    this.shield = shield;
    load(shield, xml, type, isDetect);
  }

  //used for endpoints
  Metadata(Shield shield, String itemsString, boolean caseSensitive, boolean includeEndpointAttributes, String endpointIsStrict, com.sanwaf.log.Logger logger, boolean isDetect) {
    this.logger = logger;
    loadEndpoints(shield, itemsString, caseSensitive, includeEndpointAttributes, isDetect);

    if ("true".equalsIgnoreCase(endpointIsStrict)) {
      this.endpointIsStrict = true;
    } else if ("<".equals(endpointIsStrict) || "less".equalsIgnoreCase(endpointIsStrict)) {
      this.endpointIsStrict = true;
      this.endpointIsStrictAllowLess = true;
    }
  }

  void load(Shield shield, Xml xml, String type, boolean isDetect) {
    String metadataBlock = xml.get(XML_METADATA);
    Xml metadataBlockXml = new Xml(metadataBlock);
    String securedBlock = metadataBlockXml.get(XML_SECURED);
    Xml securedBlockXml = new Xml(securedBlock);

    String enabledViewBlock = metadataBlockXml.get(Shield.XML_ENABLED);
    Xml enabledViewdBlockXml = new Xml(enabledViewBlock);
    enabled = Boolean.parseBoolean(enabledViewdBlockXml.get(type));

    String caseBlock = metadataBlockXml.get(Shield.XML_CASE_SENSITIVE);
    Xml caseBlockXml = new Xml(caseBlock);
    caseSensitive = Boolean.parseBoolean(caseBlockXml.get(type));

    String subBlock = securedBlockXml.get(type);
    Xml subBlockXml = new Xml(subBlock);
    String[] xmlItems = subBlockXml.getAll(ItemFactory.XML_ITEM);
    for (String itemString : xmlItems) {
      loadItem(shield, itemString, false, isDetect);
    }
  }

  private void loadItem(Shield shield, String itemString, boolean includeEnpointAttributes, boolean isDetect) {
    Xml xml = new Xml(itemString);
    Item item = ItemFactory.parseItem(shield, xml, includeEnpointAttributes, logger);
    //do we want to load this item for the provided isDetect parm
    if((isDetect && item.mode != null && (item.mode == Modes.BLOCK || item.mode == Modes.DISABLED)) ||
       (!isDetect && (item.mode != null && (item.mode == Modes.DETECT || item.mode == Modes.DETECT_ALL)))) {
      return;
    }
    String namesString = xml.get(ItemFactory.XML_ITEM_NAME);

    if (namesString.contains(Shield.SEPARATOR)) {
      String[] names = namesString.split(Shield.SEPARATOR);
      Item thisItem = item;
      for (String name : names) {
        name = refineName(name, index);
        if (name == null) {
          continue;
        }
        if (!caseSensitive) {
          name = name.toLowerCase();
        }
        if(thisItem == null) {
        	thisItem = ItemFactory.parseItem(shield, xml, includeEnpointAttributes, logger);
        }
        thisItem.name = name;
        thisItem.display = name;
        items.put(name, thisItem);
      }
    } else {
      item.name = refineName(item.name, index);
      if (item.name != null) {
        if (!caseSensitive) {
          item.name = item.name.toLowerCase();
        }
        items.put(item.name, item);
      }
    }
  }

  void loadEndpoints(Shield shield, String itemsString, boolean caseSensitive, boolean includeEndpointAttributes, boolean isDetect) {
    enabled = true;
    this.caseSensitive = caseSensitive;

    Xml itemsXml = new Xml(itemsString);
    String[] xmlItems = itemsXml.getAll(ItemFactory.XML_ITEM);
    for (String itemString : xmlItems) {
      loadItem(shield, itemString, includeEndpointAttributes, isDetect);
    }
  }

  static String charString(char c) {
    if (c < 128) {
      return CHAR_STRINGS[c];
    }
    return String.valueOf(c);
  }

  static String refineName(String name, Map<String, Set<String>> map) {
    int last = 0;
    while (true) {
      int starPos = name.indexOf(STAR, last);
      if (starPos < 0) {
        return name;
      }
      if (starPos == 0) {
        return null;
      }
      String f = charString(name.charAt(starPos - 1));
      String markerChars;

      if (starPos == name.length() - 1) {
        markerChars = INDEX_PARM_MARKER + name.substring(0, name.length() - 1);
      } else {
        markerChars = f + charString(name.charAt(starPos + 1));
        if (!isNotAlphanumeric(markerChars)) {
          return null;
        }
      }
      String firstCharOfKey = charString(name.charAt(0));
      Set<String> chars = map.computeIfAbsent(firstCharOfKey, k -> new LinkedHashSet<>());
      chars.add(markerChars);
      name = name.substring(0, starPos) + name.substring(starPos + 1, name.length());
    }
  }

  static String stripEosNumbers(final String s) {
    int i = s.length() - 1;
    if (i <= 0) {
      return s;
    }
    char c = s.charAt(i);
    if (c < '0' || c > '9') {
      return s;
    }
    i--;
    while (i > 0) {
      c = s.charAt(i);
      if (c < '0' || c > '9') {
        return s.substring(0, i + 1);
      }
      i--;
    }
    return s;
  }

  static boolean isNotAlphanumeric(String s) {
    char[] chars = s.toCharArray();
    for (char c : chars) {
      if (!(c < 0x30 || (c >= 0x3a && c <= 0x40) || (c > 0x5a && c <= 0x60) || c > 0x7a)) {
        return false;
      }
    }
    return true;
  }

  static String jsonEncode(String s) {
    if (s == null) {
      return "";
    } else {
      s = s.replace("\\", "\\\\");
      s = s.replace("\"", "\\\"");
      return s.replace("/", "\\/");
    }
  }

  String getFromIndex(String key) {
    if (key == null || key.length() == 0) {
      return null;
    }
    String firstChar = charString(key.charAt(0));
    Set<String> set = index.get(firstChar);
    if (set == null) {
      return null;
    }

    for (String s : set) {
      int last = 0;
      while (true) {
        if (s.length() != 2) {
          return resolveStarAtEndOfWord(key, set);
        }
        char c0 = s.charAt(0);
        char c1 = s.charAt(1);
        int start = key.indexOf(c0, last);
        if (start <= 0) {
          break;
        }
        int end = key.indexOf(c1, start + 1);
        last = end + 1;
        key = key.substring(0, start + 1) + key.substring(end, key.length());
      }
    }
    return key;
  }

  private String resolveStarAtEndOfWord(String key, Set<String> set) {
    String k2 = stripEosNumbers(key);
    if (set.contains(INDEX_PARM_MARKER + k2)) {
      return k2;
    }
    return null;
  }

  Item getItem(String key) {
    return items.get(key);
  }
}
