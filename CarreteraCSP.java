package cc.carretera;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import org.jcsp.lang.*;

public class CarreteraCSP implements Carretera, CSProcess {
  // Ej. private Any2One chOp;
  private Any2OneChannel chAvanzar;
  private Any2OneChannel chEntrar;
  private Any2OneChannel chSalir;
  private Any2OneChannel chCirculando;
  private Any2OneChannel chTick;

  // Clase interna que representa un coche
  // Cada coche tiene un identificador y un número de tks (ticks)
  class Car {
    String id;
    int tks;

    Car(String id, int tks) {
      this.id = id;
      this.tks = tks;
    }
  }

  public class PetAvanzar {
    String car;
    int tks;
    // Canal de respuesta para enviar la posición del coche
    One2OneChannel respuesta;

    PetAvanzar(String car, int tks) {
      this.car = car;
      this.tks = tks;
      this.respuesta = Channel.one2one();
    }
  }

  public class PetEntrar {
    String car;
    int tks;
    // Canal de respuesta para enviar la posición del coche
    One2OneChannel respuesta;

    PetEntrar(String car, int tks) {
      this.car = car;
      this.tks = tks;
      this.respuesta = Channel.one2one();
    }
  }

  public class PetSalir {
    String car;
    // Canal de respuesta para confirmar la salida del coche
    One2OneChannel respuesta;

    PetSalir(String car) {
      this.car = car;
      this.respuesta = Channel.one2one();
    }
  }

  public class PetCirculando {
    String car;
    // Canal de respuesta para confirmar que el coche está circulando
    One2OneChannel respuesta;

    PetCirculando(String car) {
      this.car = car;
      this.respuesta = Channel.one2one();
    }
  }

  public class PetTick {
    // Canal de respuesta para confirmar el tick
    One2OneChannel respuesta;

    PetTick() {
      this.respuesta = Channel.one2one();
    }
  }

  // Configuración de la carretera
  private final int segmentos;
  private final int carriles;

  public CarreteraCSP(int segmentos, int carriles) {
    this.segmentos = segmentos;
    this.carriles = carriles;

    /* Canales de comunicacion para las distintas peticiones */
    chAvanzar = Channel.any2one();
    chEntrar = Channel.any2one();
    chSalir = Channel.any2one();
    chCirculando = Channel.any2one();
    chTick = Channel.any2one();

    // Puesta en marcha del servidor: alternativa sucia (desde el
    // punto de vista de CSP) a Parallel que nos ofrece JCSP para
    // poner en marcha un CSProcess
    new ProcessManager(this).start();
  }

  public Pos entrar(String car, int tks) {
    PetEntrar peticion = new PetEntrar(car, tks);
    // Envio de petición al servidor
    chEntrar.out().write(peticion);
    // Espera a recibir la respuesta del servidor
    Pos posicion = (Pos) peticion.respuesta.in().read();
    return posicion;
  }

  public Pos avanzar(String car, int tks) {
    PetAvanzar peticion = new PetAvanzar(car, tks);
    // mensaje al server para que ejecute avanzar
    chAvanzar.out().write(peticion);
    // Espera a recibir la respuesta del servidor
    Pos posicion = (Pos) peticion.respuesta.in().read();
    return posicion;
  }

  public void salir(String car) {
    PetSalir peticion = new PetSalir(car);
    // mensaje al server para que ejecute salir
    chSalir.out().write(peticion);
    // Espera a recibir la respuesta del servidor
    peticion.respuesta.in().read();
  }

  public void circulando(String car) {
    PetCirculando peticion = new PetCirculando(car);
    // mensaje al server para que ejecute circulando
    chCirculando.out().write(peticion);
    // Espera a recibir la respuesta del servidor
    peticion.respuesta.in().read();
  }

  public void tick() {
    PetTick peticion = new PetTick();
    // mensaje al server para que ejecute tick
    chTick.out().write(peticion);
    // Espera a recibir la respuesta del servidor
    peticion.respuesta.in().read();
  }

  public class Carretera {
    Car[][] carretera;
    int[] carrilesLibres;
    HashMap<String, Pos> posiciones = new HashMap<String, Pos>();;

    Carretera(int segmentos, int carriles) {
      this.carretera = new Car[segmentos][carriles];
      this.carrilesLibres = new int[segmentos];
      for (int segmento = 0; segmento < segmentos; segmento++) {
        for (int carril = 0; carril < carriles; carril++) {
          carretera[segmento][carril] = null;
        }
        carrilesLibres[segmento] = carriles;
      }
    }

    public Car obtener_coche(String id) {
      Pos posicion = posiciones.get(id);
      Car resultado = null;
      if (posicion != null) {
        resultado = carretera[posicion.getSegmento() - 1][posicion.getCarril() - 1];
      }
      return resultado;
    }

    public void eliminar_posicion(int segmento, int carril, String id) {
      carretera[segmento][carril] = null;
      carrilesLibres[segmento] = carrilesLibres[segmento] + 1;
    }

    /*
     * Es una función auxiliar que hace mas facil de leer el codigo se devuelve null
     * en caso de que no se pueda devolver una posicion pero esa parte del codigo
     * deberia no
     * ser alcanzable dado que en caso de no haber ningun carril libre se bloquearia
     * la ejecucion
     */
    public Pos asignar_posicion(int segmento, Car coche) {
      Car[] segmento_carretera = carretera[segmento];
      Pos posicion = null;
      for (int carril = 0; carril < carriles && posicion == null; carril++) {
        if (segmento_carretera[carril] == null) {
          // Se asigna la posicion de la carretera
          segmento_carretera[carril] = coche;
          // Se actualizan las estructuas de datos que permiten que modelizan el recurso y
          // facilitan la busqueda
          carrilesLibres[segmento] = carrilesLibres[segmento] - 1;
          posicion = new Pos(segmento + 1, carril + 1);
          posiciones.put(coche.id, posicion);
        }
      }
      return posicion;
    }

    public void tick() {
      System.out.println("Tick de la carretera");
      for (String carId : posiciones.keySet()) {
        Pos pos = posiciones.get(carId);
        Car coche = carretera[pos.getSegmento() - 1][pos.getCarril() - 1];
        if (coche.tks > 0) {
          coche.tks--;
        }
      }
    }

    public void print_state() {
      System.out.println("Estado de la carretera:");
      System.out.println("Carriles libres por segmento: " + java.util.Arrays.toString(carrilesLibres));
      System.out.println(posiciones.toString());
    }
  }

  // Código del servidor
  public void run() {

    // Definición de constantes para las operaciones
    final int ENTRAR = 0;
    final int AVANZAR = 1;
    final int SALIR = 2;
    final int CIRCULANDO = 3;
    final int TICK = 4;

    final Guard[] guardas = new Guard[5];
    guardas[ENTRAR] = chEntrar.in();
    guardas[AVANZAR] = chAvanzar.in();
    guardas[SALIR] = chSalir.in();
    guardas[CIRCULANDO] = chCirculando.in();
    guardas[TICK] = chTick.in();

    Carretera carretera = new Carretera(segmentos, carriles);
    /*
     * Estructura de datos para almacenar los coches que estan circulando
     * y su orden de prioridad basado en los ticks restantes.
     * Se utiliza una PriorityQueue para mantener el orden de los coches
     * Que deben ser desencolados primero
     */
    PriorityQueue<Car> coches_circulando = new PriorityQueue<>((a, b) -> a.tks - b.tks);
    /* Estructura de datos para asociar una peticion no atendida con un coche */
    HashMap<String, PetCirculando> peticiones_circulando = new HashMap<>();
    /*
     * Estructura de datos para almacenar los coches que desean avanzar
     * en caso de que no haya carriles libres en el siguiente segmento.
     */
    List<Queue<PetAvanzar>> peticionesAvanzar = new ArrayList<>(segmentos);
    for (int i = 0; i < segmentos; i++) {
      peticionesAvanzar.add(new LinkedList<>());
    }
    /*
     * Estructura de datos para almacenar los coches que desean entrar
     * en caso de que no haya carriles libres en el siguiente segmento.
     */
    Queue<PetEntrar> peticionesEntrar = new LinkedList<>();

    // TODO: cambiar null por el array de canales
    Alternative servicios = new Alternative(guardas);

    // Bucle principal del servicio
    while (true) {

      int servicio = servicios.fairSelect();
      System.out.println(servicio + " seleccionado");


      // TODO: ejecutar la operación solicitada por el cliente
      switch (servicio) {
        case ENTRAR:
          PetEntrar peticionEntrar = (PetEntrar) chEntrar.in().read();
          if (carretera.carrilesLibres[0] > 0) {
            // Si hay carriles libres, asignar posición
            Car coche = new Car(peticionEntrar.car, peticionEntrar.tks);
            Pos posicion = carretera.asignar_posicion(0, coche);
            peticionEntrar.respuesta.out().write(posicion);
          } else {
            // Si no hay carriles libres, almacenar la petición
            peticionesEntrar.add(peticionEntrar);
          }
          break;
        case AVANZAR:
          PetAvanzar peticionAvanzar = (PetAvanzar) chAvanzar.in().read();
          Pos pos_coche = carretera.posiciones.get(peticionAvanzar.car);
          System.out.println(pos_coche.toString());
          if (carretera.carrilesLibres[pos_coche.getSegmento()] > 0) {
            // Si hay carriles libres, asignar posición
            Car coche = carretera.obtener_coche(peticionAvanzar.car);
            coche.tks = peticionAvanzar.tks;
            Pos posicion = carretera.asignar_posicion(pos_coche.getSegmento(), coche);
            carretera.eliminar_posicion(pos_coche.getSegmento() - 1, pos_coche.getCarril() - 1, peticionAvanzar.car);
            peticionAvanzar.respuesta.out().write(posicion);
          } else {
            // Si no hay carriles libres, almacenar la petición
            Queue<PetAvanzar> lista = peticionesAvanzar.get(pos_coche.getSegmento());
            lista.add(peticionAvanzar);
          }
          break;
        case SALIR:
          PetSalir peticionSalir = (PetSalir) chSalir.in().read();
          Pos pos_coche_salir = carretera.posiciones.get(peticionSalir.car);
          carretera.eliminar_posicion(pos_coche_salir.getSegmento() - 1, pos_coche_salir.getCarril() - 1,
              peticionSalir.car);
          carretera.posiciones.remove(peticionSalir.car);
          peticionSalir.respuesta.out().write(null);
          break;
        case CIRCULANDO:
          PetCirculando peticionCirculando = (PetCirculando) chCirculando.in().read();
          Pos pos_coche_circulando = carretera.posiciones.get(peticionCirculando.car);
          Car coche_circulando = carretera.carretera[pos_coche_circulando.getSegmento() - 1][pos_coche_circulando.getCarril() - 1];
          if (coche_circulando.tks > 0) {
            coches_circulando.add(coche_circulando);
            peticiones_circulando.put(peticionCirculando.car, peticionCirculando);
          } else {
            peticionCirculando.respuesta.out().write(null);
          }
          break;
        case 4:
          PetTick peticion_tick = (PetTick) chTick.in().read();
          carretera.tick();
          peticion_tick.respuesta.out().write(null);
          break;
      }
      carretera.print_state();
      System.out.println("Estado de los coches circulando: " + coches_circulando.toString());
      System.out.println("El mapeo entre circulando y peticiones: " + peticiones_circulando.toString());
      System.out.println("Peticiones de entrar: " + peticionesEntrar.toString());
      System.out.println("Peticiones de avanzar: " + peticionesAvanzar.toString());

      /* Desbloquear aquellos coches que esten circulando y se cumple su CPRE */
      if (!coches_circulando.isEmpty()) {
        Car coche = coches_circulando.peek();
        boolean extraer = true;
        System.out.println("Coches circulando: " + coches_circulando.toString());
        while (coche != null && extraer) {
          if (coche.tks == 0) {
            PetCirculando peticion = peticiones_circulando.get(coche.id);
            peticion.respuesta.out().write(null);
            coches_circulando.poll();
            peticiones_circulando.remove(coche.id);
          } else {
            extraer = false;
          }
          coche = coches_circulando.peek();
        }
      }
      // Peticiones de avanzar
      if (!peticionesAvanzar.isEmpty()) {
        // Comprobamos si hay carriles libres para cada segmento
        for (int i = peticionesAvanzar.size() - 1; i > 0; i--) {
          // Rellenamos con peticiones hasta que no queden carriles libres
          int plibres = carretera.carrilesLibres[i];
          Queue<PetAvanzar> peticiones = peticionesAvanzar.get(i);
          System.out.println("Avanzar segemento " + i + " con carriles libres: " + plibres);
          while (plibres > 0 && !peticiones.isEmpty()) {
            PetAvanzar peticion = peticiones.peek();
            if (peticion != null) {
              // Si hay carriles libres, asignar posición
              Car coche = carretera.obtener_coche(peticion.car);
              Pos pos_coche = carretera.posiciones.get(peticion.car);
              Pos posicion = carretera.asignar_posicion(pos_coche.getSegmento(), coche);
              carretera.eliminar_posicion(pos_coche.getSegmento() - 1, pos_coche.getCarril() - 1, peticion.car);
              coche.tks = peticion.tks;
              peticionesAvanzar.get(i).poll();
              peticion.respuesta.out().write(posicion);
              peticion = peticiones.peek();
            }
            plibres--;
          }
        }
      }
      if (!peticionesEntrar.isEmpty()) {
        // Comprobamos si hay carriles libres en el primer segmento
        if (carretera.carrilesLibres[0] > 0) {
          PetEntrar peticion = peticionesEntrar.poll();
          if (peticion != null) {
            // Si hay carriles libres, asignar posición
            Car coche = new Car(peticion.car, peticion.tks);
            Pos posicion = carretera.asignar_posicion(0, coche);
            peticion.respuesta.out().write(posicion);
          }
        }
      }
      System.out.println("Fin desbloqueo de coches circulando y peticiones pendientes");
    }
    // TODO: atender peticiones pendientes que puedan ser atendida
  }
}
