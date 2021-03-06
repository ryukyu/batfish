package org.batfish.datamodel.vendor_family.cisco;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.batfish.common.util.ComparableStructure;

public class DepiClass extends ComparableStructure<String> {

  /** */
  private static final long serialVersionUID = 1L;

  private final int _definitionLine;

  @JsonCreator
  private DepiClass(@JsonProperty(PROP_NAME) String name) {
    super(name);
    _definitionLine = -1;
  }

  public DepiClass(String number, int definitionLine) {
    super(number);
    _definitionLine = definitionLine;
  }

  @JsonIgnore
  public int getDefinitionLine() {
    return _definitionLine;
  }

}
