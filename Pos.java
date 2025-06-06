package cc.carretera;

/**
 * Una posicion en la carretera: un segmento y un carril.
 */
public class Pos {
  private int segmento;
  private int carril;

  /**
   * Crea una posicion.
   */
  public Pos(int segmento, int carril) {
    if (segmento <= 0 || carril <= 0) {
      throw new IllegalArgumentException("una posicion siempre tiene un segmento > 0 y carril > 0");
    }
    this.segmento = segmento;
    this.carril = carril;
  }

  /**
   * Devuelve el segmento.
   */
  public int getSegmento() {
    return segmento;
  }

  /**
   * Devuelve el carril.
   */
  public int getCarril() {
    return carril;
  }

  public boolean equals(Object obj) {
    if (obj instanceof Pos) {
      Pos p = (Pos) obj;
      return p.getSegmento() == getSegmento() && p.getCarril() == getCarril();
    } return false;
  }

  public int hashCode() {
    return getSegmento()*100 + getCarril();
  }

  public String toString() {
	  return "Pos("+getSegmento()+","+getCarril()+")";
  }
}
