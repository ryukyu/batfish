package org.batfish.representation.cisco;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.batfish.datamodel.Ip6Wildcard;
import org.batfish.datamodel.IpProtocol;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.State;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.TcpFlags;

public class StandardIpv6AccessListLine implements Serializable {

  private static final long serialVersionUID = 1L;

  private final LineAction _action;

  private final Set<Integer> _dscps;

  private final Set<Integer> _ecns;

  private final Ip6Wildcard _ipWildcard;

  private final String _name;

  public StandardIpv6AccessListLine(
      String name,
      LineAction action,
      Ip6Wildcard ipWildcard,
      Set<Integer> dscps,
      Set<Integer> ecns) {
    _name = name;
    _action = action;
    _ipWildcard = ipWildcard;
    _dscps = dscps;
    _ecns = ecns;
  }

  public LineAction getAction() {
    return _action;
  }

  public Ip6Wildcard getIpWildcard() {
    return _ipWildcard;
  }

  public String getName() {
    return _name;
  }

  public ExtendedIpv6AccessListLine toExtendedIpv6AccessListLine() {
    return new ExtendedIpv6AccessListLine(
        _name,
        _action,
        IpProtocol.IP,
        _ipWildcard,
        null,
        Ip6Wildcard.ANY,
        null,
        Collections.<SubRange>emptyList(),
        Collections.<SubRange>emptyList(),
        _dscps,
        _ecns,
        null,
        null,
        EnumSet.noneOf(State.class),
        Collections.<TcpFlags>emptyList());
  }
}
