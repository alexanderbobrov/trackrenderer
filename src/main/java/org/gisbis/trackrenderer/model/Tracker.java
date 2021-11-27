package org.gisbis.trackrenderer.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode ( onlyExplicitlyIncluded = true )
@AllArgsConstructor
@NoArgsConstructor
public class Tracker {
	
	@EqualsAndHashCode.Include
	private int id;
	
	private String imei;

	private String phone;
	
	private String name;
}
