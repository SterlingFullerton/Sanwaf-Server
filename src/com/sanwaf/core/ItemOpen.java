package com.sanwaf.core;

import java.util.Collections;
import java.util.List;

import jakarta.servlet.ServletRequest;

class ItemOpen extends Item {
  ItemOpen(ItemData id) {
    super(id);
  }

  @Override
  boolean inError(final ServletRequest req, final Shield shield, final String value, boolean doAllBlocks, boolean log) {
    if (hasPreValidationError(req, value)) {
      return true;
    }
    return false;
  }

  @Override
  List<Point> getErrorPoints(Shield shield, String value) {
    if (maskError.length() > 0) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new Point(0, value.length()));
  }

  @Override
  Types getType() {
    return Types.OPEN;
  }
}

