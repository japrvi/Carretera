package cc.carretera;

import java.util.HashMap;
import java.util.PriorityQueue;

import es.upm.babel.cclib.Monitor;

/**
 * Implementación del recurso compartido Carretera con Monitores
 */
public class CarreteraMonitor implements Carretera {
  //  La clase Car representa la caracteristicas de un coche
  //  Dispone de un tks propio y un contador tksConsumido que baja segun va circulando
  class Car{
    String id;
    int tks;
    Monitor.Cond circulando;
    Car(String id, int tks, Monitor mutex){
      this.id = id;
      this.tks = tks;
      circulando = mutex.newCond();
    }
  }
  //  Definicion del monitor y las estucturas de datos que gestionan los cond
  Monitor mutex;
  //  Representa una estructura de datos de ConditionQueues por cada segemento se podrá 
  //  bloquear el coche hasta que queden libres los carriles del siguiente segemento
  private Monitor.Cond [] espera;
  
  //  Definición de la estructura de datos que representa el recurso
  private Car [][] carretera;
  private int segmentos;
  private int carriles;
  //  Posiciones asocia a un identificador de un coche a una carretera
  //  A la hora de actualizar velocidades llevar u registro de las posiciones evitara
  // cosnultas innecesarias
  private HashMap<String, Pos> posiciones;
  private int [] carrilesLibres;
  private PriorityQueue<Car> coches_circulando;

  public CarreteraMonitor(int segmentos, int carriles) 
  {
    this.segmentos = segmentos;
    this.carriles = carriles;;
    mutex = new Monitor();
    espera = new Monitor.Cond[segmentos];
    posiciones = new HashMap<String, Pos>();
    coches_circulando = new PriorityQueue<Car>((a, b) -> a.tks - b.tks);
    carretera = new Car[segmentos][carriles];
    carrilesLibres = new int [segmentos];
    //  Se separa la inicializacion en dos bloques tanto en cuanto la cantidad de 
    //  Cond es menor que la cantidad de segementos
    for (int segmento=0; segmento<segmentos; segmento++)
    {
      for (int carril=0; carril<carriles; carril++)
        carretera[segmento][carril] = null;
      carrilesLibres[segmento] = carriles;
      espera[segmento] = mutex.newCond();
    }
  }

  public Pos entrar(String id, int tks) {
    mutex.enter();
    if (carrilesLibres[0] == 0)
      espera[0].await();
    Car coche = new Car(id, tks, this.mutex);
    Pos res = asignar_posicion(0, coche);
    //desbloqueo_general();
    mutex.leave();
    return res;
  }

  public Pos avanzar(String id, int tks) {
    mutex.enter();
    Pos pos = posiciones.get(id);
    int segmento = pos.getSegmento() - 1;
    int carril = pos.getCarril() - 1;
    if (carrilesLibres[segmento + 1] == 0)
      espera[segmento + 1].await();
    Car coche = new Car(id, tks, mutex);
    Pos asignar = asignar_posicion(segmento + 1, coche);
    eliminar_posicion(segmento, carril, id);
    desbloqueo_general();
    mutex.leave();
    return asignar;
  }

  public void salir(String id) {
    mutex.enter();
    Pos posicion = posiciones.get(id);
    int segmento = posicion.getSegmento() - 1;
    int carril = posicion.getCarril() - 1;
    eliminar_posicion(segmento, carril, id);
    posiciones.remove(id);
    desbloqueo_general();
    mutex.leave();
  }
  
  /*  */
  public void circulando(String id) {
    mutex.enter();
    int segmento = posiciones.get(id).getSegmento() - 1;
    int carril = posiciones.get(id).getCarril() - 1;
    Car coche = carretera[segmento][carril];
    if (coche.tks > 0)
    {
      coches_circulando.add(coche);
      coche.circulando.await();
    }
    desbloqueo_general();
    mutex.leave();
  }

  /*  Tick baja en uno a cada coche en un bucle capturando el cerrojo de manera que
   *  en el mismo instante todos bajas su tks, en caso de que el tks sea cero se intentaría
   *  desbloquear el coche si este esta circulando.
   */
  public void tick() {
    mutex.enter();
    for (String car : posiciones.keySet()) {
      Pos posicion = posiciones.get(car);
      int segmento = posicion.getSegmento() - 1;
      int carril = posicion.getCarril() - 1;
      Car coche = carretera[segmento][carril];
      int tks = coche.tks;
      if (tks > 0)
        coche.tks--;
      }
    desbloqueo_general();
    mutex.leave();
  }
/* Funcion que administra el desbloqueo*/
  private void desbloqueo_general()
  {
    //desbloqueo_circulando()
    if (desbloqueo_circulando() == false)
    {
      desbloqueo_avanzar();
    }
  }

/*
 * Funcion que desbloquea el coche que esta circulando en caso de que su tks sea 0
 * Para ello comprueba en la cola con prioridad si el primer coche
 * tiene tks == 0 extrae el coche de la cola y lo desbloquea
 * En caso de que no haya coches en la cola no se hace nada 
 * Se devuelve false siempe que no se desbloquee ningun coche
*/
  private boolean desbloqueo_circulando()
  {
    boolean resultado = false;
    if (!coches_circulando.isEmpty())
    {
      Car coche = coches_circulando.peek();
      if (coche.tks == 0)
      {
        coche = coches_circulando.poll();
        // Desbloquea el coche que esta circulando
        coche.circulando.signal();
        resultado = true;
      }
    }
    return resultado;
  }

  /*
   * Funcion que desbloquea el coche que quiere avanzar, comprueba si existen procesos
   * bloqueados en cualquiera de los segmentos empezando por el último segmento
   * Si se cumple la CPRE adecuada para ello se desbloquea y se se devuelve true
   * En el otro caso se devuelve false
   */
  private boolean desbloqueo_avanzar()
  {
    boolean resultado = false;
    for (int segmento = segmentos - 1; segmento >= 0 && !resultado; segmento--)
    {
      if (espera[segmento].waiting() > 0)
      {
        if (carrilesLibres[segmento] > 0)
          espera[segmento].signal();
        resultado = true;
      }
    }
    return resultado;
  }

  //  Funcion que elimina el coche de la posición de manera que todas las estructuras
  //  de datos usadas se actualizan
  private void eliminar_posicion(int segmento, int carril, String id)
  {
    carretera[segmento][carril] = null;
    carrilesLibres[segmento] = carrilesLibres[segmento] + 1;
    //posiciones.remove(id);
  }
  
  /* Es una función auxiliar que hace mas facil de leer el codigo se devuelve null
   * en caso de que no se pueda devolver una posicion pero esa parte del codigo deberia no
   * ser alcanzable dado que en caso de no haber ningun carril libre se bloquearia la ejecucion
   */
  private Pos asignar_posicion(int segmento, Car coche)
  {
    Car [] segmento_carretera = carretera[segmento];
    Pos posicion = null;
    for (int carril=0; carril<carriles && posicion == null; carril++)
    {
      if (segmento_carretera[carril] == null)
      {
        // Se asigna la posicion de la carretera
        segmento_carretera[carril] = coche;
        // Se actualizan las estructuas de datos que permiten que modelizan el recurso y facilitan la busqueda
        carrilesLibres[segmento] = carrilesLibres[segmento] - 1;
        posicion =  new Pos(segmento + 1, carril + 1);
        posiciones.put(coche.id, posicion);
      }
    }
    return posicion;
  }

   void print_state()
   {
    for (int i=0; i<segmentos; i++)
      System.out.println("El valor de carrilesLibres[" + i + "] es = " + carrilesLibres[i]);
    System.out.println("El valor de posiciones es = " + posiciones.toString());
   }
}
