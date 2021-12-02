package org.gisbis.geo;

import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;

/** gis bis athine transform implementation */
public class AthinesTransform {
		
	public double a0;
	
	public double a1;
	
	public double a2;
	
	public double b0;
	
	public double b1;
	
	public double b2;
	
	/** Преобразует координаты из исходной системы в требуемую */
	public Coordinate transform ( Coordinate source ) {
		return new Coordinate( b0 + b1*source.x + b2*source.y , a0 + a1*source.x + a2*source.y );
	}
	
	/** Преобразует координаты из исходной системы в требуемую */
	public List<Coordinate> transform ( List<Coordinate> source ) {
		return source.parallelStream().map(c->transform(c)).collect(Collectors.toList());
	}
	
	
	
	/**
	 * Создает афинное преобразование из квадрата координат 3857 в экранные
	 * @param width - ширина экрана
	 * @param height - высота экрана
	 * @param min - координата левого нижнего угла в 3857
	 * @param max - координата правого верхнего угла в 3857
	 * @return объект преобразования
	 */
	public AthinesTransform ( int width, int height, Coordinate min, Coordinate max ) {
		if ( min.x > max.x ) {
			double tmp = min.x;
			min.setX(max.x);
			max.setX(tmp);
		}
		if ( min.y > max.y ) {
			double tmp = min.y;
			min.setY(max.y);
			max.setY(tmp);
		}
		Coordinate dmin = new Coordinate(0, height);
		Coordinate dmax = new Coordinate(width,0);
		Coordinate sp = new Coordinate(max.getX(),min.getY());
		Coordinate dp = new Coordinate(width, height);
		
		initializeCoefficients( min, sp, max, dmin, dp, dmax );
	} 
	
	/**
	 * Создает афинное преобразование из трех пар сопоставленных координат разных систем
	 * @param s1 - первая координата исходной системы
	 * @param s2 - вторая координата исходной системы
	 * @param s3 - третья координата исходной системы
	 * @param d1 - координата в требуемой системе, соответствующая первой координате исходной системы
	 * @param d2 - координата в требуемой системе, соответствующая второй координате исходной системы
	 * @param d3 - координата в требуемой системе, соответствующая третьей координате исходной системы
	 */
	public AthinesTransform ( Coordinate s1, Coordinate s2, Coordinate s3, Coordinate d1, Coordinate d2, Coordinate d3 ) {		
		initializeCoefficients(s1, s2, s3, d1, d2, d3);		
	}
	
	/**
	 * Инициализировать коэффициэнты для афинного преобразования из трех пар сопоставленных координат разных систем
	 * @param s1 - первая координата исходной системы
	 * @param s2 - вторая координата исходной системы
	 * @param s3 - третья координата исходной системы
	 * @param d1 - координата в требуемой системе, соответствующая первой координате исходной системы
	 * @param d2 - координата в требуемой системе, соответствующая второй координате исходной системы
	 * @param d3 - координата в требуемой системе, соответствующая третьей координате исходной системы
	 */
	public void initializeCoefficients ( Coordinate s1, Coordinate s2, Coordinate s3, Coordinate d1, Coordinate d2, Coordinate d3 ) {
		a2 = ((d3.y-d1.y)*(s2.x-s1.x)-(d2.y-d1.y)*(s3.x-s1.x))/
			 ((s3.y-s1.y)*(s2.x-s1.x)-(s2.y-s1.y)*(s3.x-s1.x));
		
		a1 = (d2.y-d1.y-a2*(s2.y-s1.y))/(s2.x-s1.x);
		
		a0 = d1.y - a1*s1.x - a2*s2.y;
		
		b2 = ((d3.x-d1.x)*(s2.x-s1.x)-(d2.x-d1.x)*(s3.x-s1.x))/
			 ((s3.y-s1.y)*(s2.x-s1.x)-(s2.y-s1.y)*(s3.x-s1.x));
		
		b1 = (d2.x-d1.x-b2*(s2.y-s1.y))/(s2.x-s1.x);
		
		b0 = d1.x - b1*s1.x - b2*s2.y;
	}
	
}
