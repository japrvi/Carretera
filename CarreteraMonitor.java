package cc.carretera;

import java.util.HashMap;

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

  public CarreteraMonitor(int segmentos, int carriles) 
  {
    this.segmentos = segmentos;
    this.carriles = carriles;;
    mutex = new Monitor();
    espera = new Monitor.Cond[segmentos];
    posiciones = new HashMap<String, Pos>();
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
    print_state();
    if (carrilesLibres[0] == 0)
      espera[0].await();
    Car coche = new Car(id, tks, this.mutex);
    Pos res = asignar_posicion(0, coche);
    //desbloqueo_espera(0);
    print_state();
    mutex.leave();
    return res;
  }

  public Pos avanzar(String id, int tks) {
    mutex.enter();
    System.out.println(posiciones.toString());
    Pos pos = posiciones.get(id);
    int segmento = pos.getSegmento() - 1;
    int carril = pos.getCarril() - 1;
    if (carrilesLibres[segmento + 1] == 0)
      espera[segmento + 1].await();
    Car coche = new Car(id, tks, mutex);
    Pos asignar = asignar_posicion(segmento + 1, coche);
    System.out.println(asignar.toString());
    eliminar_posicion(segmento, carril, id);
    desbloqueo_espera(segmento);
    mutex.leave();
    return asignar;
  }

  public void salir(String id) {
    mutex.enter();
    Pos posicion = posiciones.get(id);
    int segmento = posicion.getSegmento();
    int carril = posicion.getCarril();
    if (segmento == this.segmentos -1)
      desbloqueo_espera(segmento);
    eliminar_posicion(segmento, carril, id);
    mutex.leave();
  }
  
  /*  */
  public void circulando(String id) {
    mutex.enter();
    print_state();
    int segmento = posiciones.get(id).getSegmento() - 1;
    int carril = posiciones.get(id).getCarril() - 1;
    Car coche = carretera[segmento][carril];
    if (coche.tks > 0)
      coche.circulando.await();
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
      else
        desbloqueo_circulando(coche);
    }
    mutex.leave();
  }

  private void desbloqueo_espera(int segmento)
  {
    if (carrilesLibres[segmento] > 0)
      espera[segmento].signal();
  }

  //  Funcion que desbloquea los coches que estan circulando.
  //  Se chequea que no se cumple la CPRE y tambien que exista un coche bloqueado.
  //  Solo puede haber un coche esperando mientrras circula al ser una cola asociada
  //  a un coche. Al comprobarse en la función tks si este mayor que cero no sería necesario
  //  verificar que se cumple la CPRE.
  private void desbloqueo_circulando(Car coche)
  {
    int tks = coche.tks;
    Monitor.Cond cond= coche.circulando;
    if (tks == 0 && cond.waiting() > 0)
      cond.signal();
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
