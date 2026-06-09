package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class BookRepository(private val bookDao: BookDao) {

    val allProjects: Flow<List<BookProject>> = bookDao.getAllProjectsFlow()

    fun getProjectFlow(id: Int): Flow<BookProject?> = bookDao.getProjectByIdFlow(id)

    suspend fun getProject(id: Int): BookProject? = bookDao.getProjectById(id)

    fun getPagesFlow(projectId: Int): Flow<List<BookPage>> = bookDao.getPagesForProjectFlow(projectId)

    suspend fun getPages(projectId: Int): List<BookPage> = bookDao.getPagesForProject(projectId)

    suspend fun insertProject(project: BookProject): Int {
        return bookDao.insertProject(project).toInt()
    }

    suspend fun updateProject(project: BookProject) {
        bookDao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(projectId: Int) {
        val proj = bookDao.getProjectById(projectId)
        if (proj != null) {
            bookDao.deletePagesForProject(projectId)
            bookDao.deleteProject(proj)
        }
    }

    suspend fun insertPage(page: BookPage): Int {
        return bookDao.insertPage(page).toInt()
    }

    suspend fun updatePage(page: BookPage) {
        bookDao.updatePage(page)
    }

    suspend fun deletePage(page: BookPage) {
        bookDao.deletePage(page)
        // Re-number subsequent pages to avoid gaps
        val remainingPages = bookDao.getPagesForProject(page.projectId)
        var count = 1
        for (p in remainingPages) {
            if (p.pageNumber != count) {
                bookDao.updatePage(p.copy(pageNumber = count))
            }
            count++
        }
    }

    suspend fun createPageForProject(projectId: Int): BookPage {
        val currentPages = bookDao.getPagesForProject(projectId)
        val nextNumber = if (currentPages.isEmpty()) 1 else currentPages.maxOf { it.pageNumber } + 1
        val newPage = BookPage(
            projectId = projectId,
            pageNumber = nextNumber,
            elements = emptyList()
        )
        val id = bookDao.insertPage(newPage).toInt()
        return newPage.copy(id = id)
    }

    suspend fun populateSampleIfEmpty() {
        val existing = bookDao.getAllProjectsFlow().firstOrNull()
        if (existing.isNullOrEmpty()) {
            // Create a gorgeous Trade Novel Layout as a default tutorial
            val sampleProject = BookProject(
                title = "Estilo de Novela (Plantilla de Muestra)",
                pageWidthMm = 152.4f,  // 6x9 inches
                pageHeightMm = 228.6f,
                marginMmLeft = 20.0f,
                marginMmRight = 15.0f,
                marginMmTop = 22.0f,
                marginMmBottom = 22.0f,
                bleedMm = 3.175f
            )
            val projectId = bookDao.insertProject(sampleProject).toInt()

            // Page 1: Creative Cover
            val page1 = BookPage(
                projectId = projectId,
                pageNumber = 1,
                elements = listOf(
                    LayoutElement(
                        type = "shape",
                        xMm = 0f,
                        yMm = 0f,
                        widthMm = 152.4f,
                        heightMm = 228.6f,
                        zIndex = 0,
                        shapeType = "RECTANGLE",
                        shapeFillColorHex = "#F8FAFC", // soft offwhite paper texture
                        shapeStrokeColorHex = "#334155",
                        shapeStrokeWidthMm = 2.0f
                    ),
                    LayoutElement(
                        type = "shape",
                        xMm = 10f,
                        yMm = 10f,
                        widthMm = 132.4f,
                        heightMm = 208.6f,
                        zIndex = 1,
                        shapeType = "RECTANGLE",
                        shapeFillColorHex = "#0F172A", // Dark Slate Accent Cover Shape
                        shapeStrokeColorHex = "#64748B",
                        shapeStrokeWidthMm = 0.5f
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 20f,
                        yMm = 35f,
                        widthMm = 112.4f,
                        heightMm = 45f,
                        zIndex = 2,
                        textContent = "EL ALQUIMISTA DE SUEÑOS",
                        fontSizeSp = 28f,
                        textColorHex = "#F1F5F9",
                        textAlignment = "CENTER",
                        fontFamily = "Times New Roman",
                        isBold = true,
                        isItalic = false
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 20f,
                        yMm = 80f,
                        widthMm = 112.4f,
                        heightMm = 15f,
                        zIndex = 3,
                        textContent = "Una novela de fantasía clásica sobre el tiempo",
                        fontSizeSp = 14f,
                        textColorHex = "#94A3B8",
                        textAlignment = "CENTER",
                        fontFamily = "Times New Roman",
                        isBold = false,
                        isItalic = true
                    ),
                    LayoutElement(
                        type = "shape",
                        xMm = 56.2f,
                        yMm = 110f,
                        widthMm = 40f,
                        heightMm = 40f,
                        zIndex = 4,
                        shapeType = "ELLIPSE",
                        shapeFillColorHex = "#F59E0B", // Golden Sun Ornament
                        shapeStrokeColorHex = "#D97706",
                        shapeStrokeWidthMm = 1.0f
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 20f,
                        yMm = 175f,
                        widthMm = 112.4f,
                        heightMm = 20f,
                        zIndex = 5,
                        textContent = "AUTOR ADRIÁN SILVA",
                        fontSizeSp = 16f,
                        textColorHex = "#E2E8F0",
                        textAlignment = "CENTER",
                        fontFamily = "Sans-Serif",
                        isBold = true
                    )
                )
            )

            // Page 2: Chapter 1 Page
            val page2 = BookPage(
                projectId = projectId,
                pageNumber = 2,
                elements = listOf(
                    LayoutElement(
                        type = "text",
                        xMm = 20f,
                        yMm = 30f,
                        widthMm = 112.4f,
                        heightMm = 15f,
                        zIndex = 1,
                        textContent = "CAPÍTULO I",
                        fontSizeSp = 18f,
                        textColorHex = "#0F172A",
                        textAlignment = "CENTER",
                        fontFamily = "Times New Roman",
                        isBold = true
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 20f,
                        yMm = 48f,
                        widthMm = 112.4f,
                        heightMm = 12f,
                        zIndex = 2,
                        textContent = "El Amanecer de Arena",
                        fontSizeSp = 14f,
                        textColorHex = "#475569",
                        textAlignment = "CENTER",
                        fontFamily = "Times New Roman",
                        isBold = false,
                        isItalic = true
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 20f,
                        yMm = 65f,
                        widthMm = 112.4f,
                        heightMm = 135f,
                        zIndex = 3,
                        textContent = "El sol se alzaba lentamente sobre las colinas doradas del desierto infinito. Adrián había pasado toda su vida buscando las runas ocultas en los antiguos manuscritos. Scribus fue su guía.\n\nCada página que componía con esmero revelaba un nuevo misterio del cosmos. Sus manos temblaban mientras sostenía el viejo papel, cuyos márgenes perfectos y sangrado de impresión reflejaban un arte antiguo casi olvidado. Este libro era el culmen de su viaje.",
                        fontSizeSp = 12f,
                        textColorHex = "#1E293B",
                        textAlignment = "JUSTIFY",
                        fontFamily = "Times New Roman",
                        isBold = false
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 20f,
                        yMm = 210f,
                        widthMm = 112.4f,
                        heightMm = 8f,
                        zIndex = 4,
                        textContent = "— 1 —",
                        fontSizeSp = 10f,
                        textColorHex = "#64748B",
                        textAlignment = "CENTER",
                        fontFamily = "Times New Roman"
                    )
                )
            )

            bookDao.insertPage(page1)
            bookDao.insertPage(page2)

            // --- 2. Create the customized novel "Cuando todos se van, yo me quedo" ---
            val novelProject = BookProject(
                title = "Cuando todos se van, yo me quedo",
                pageWidthMm = 152.4f, // 6x9 inches (US Trade Novel standard)
                pageHeightMm = 228.6f,
                marginMmLeft = 22.0f,
                marginMmRight = 18.0f,
                marginMmTop = 22.0f,
                marginMmBottom = 22.0f,
                bleedMm = 3.175f,
                showPageNumbers = true,
                pageNumbersStartAtPage = 3
            )
            val novelId = bookDao.insertProject(novelProject).toInt()

            // Page 1: Elegant Cover
            val novelCover = BookPage(
                projectId = novelId,
                pageNumber = 1,
                elements = listOf(
                    LayoutElement(
                        type = "shape",
                        xMm = 0f, yMm = 0f, widthMm = 152.4f, heightMm = 228.6f, zIndex = 0,
                        shapeType = "RECTANGLE", shapeFillColorHex = "#FAF7F2", shapeStrokeColorHex = "#1D2D44", shapeStrokeWidthMm = 2.0f
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 22f, yMm = 45f, widthMm = 112.4f, heightMm = 15f, zIndex = 1,
                        textContent = "RUFO GARCÍA RUBIO",
                        fontSizeSp = 15f, textColorHex = "#1E293B", textAlignment = "CENTER", fontFamily = "Sans-Serif", isBold = true
                    ),
                    LayoutElement(
                        type = "shape",
                        xMm = 51.2f, yMm = 65f, widthMm = 50f, heightMm = 0.5f, zIndex = 2,
                        shapeType = "RECTANGLE", shapeFillColorHex = "#64748B", shapeStrokeColorHex = "#64748B", shapeStrokeWidthMm = 0.1f
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 22f, yMm = 85f, widthMm = 112.4f, heightMm = 65f, zIndex = 3,
                        textContent = "Cuando todos se van,\nyo\nme quedo",
                        fontSizeSp = 22f, textColorHex = "#0F172A", textAlignment = "CENTER", fontFamily = "Times New Roman", isBold = true, isItalic = true
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 22f, yMm = 175f, widthMm = 112.4f, heightMm = 15f, zIndex = 4,
                        textContent = "EPIDAURO",
                        fontSizeSp = 13f, textColorHex = "#334155", textAlignment = "CENTER", fontFamily = "Sans-Serif", isBold = true
                    )
                )
            )
            bookDao.insertPage(novelCover)

            // Page 2: Dedication / Motto
            val novelDedication = BookPage(
                projectId = novelId,
                pageNumber = 2,
                elements = listOf(
                    LayoutElement(
                        type = "shape",
                        xMm = 0f, yMm = 0f, widthMm = 152.4f, heightMm = 228.6f, zIndex = 0,
                        shapeType = "RECTANGLE", shapeFillColorHex = "#FAF7F2", shapeStrokeColorHex = "#94A3B8", shapeStrokeWidthMm = 0.2f
                    ),
                    LayoutElement(
                        type = "text",
                        xMm = 22f, yMm = 95f, widthMm = 112.4f, heightMm = 45f, zIndex = 1,
                        textContent = "Cuando todos se van,\nyo\nme quedo",
                        fontSizeSp = 18f, textColorHex = "#1E293B", textAlignment = "CENTER", fontFamily = "Times New Roman", isItalic = true
                    ),
                    LayoutElement(
                        type = "shape",
                        xMm = 71.2f, yMm = 155f, widthMm = 10f, heightMm = 10f, zIndex = 2,
                        shapeType = "ELLIPSE", shapeFillColorHex = "#FAF7F2", shapeStrokeColorHex = "#64748B", shapeStrokeWidthMm = 0.5f
                    )
                )
            )
            bookDao.insertPage(novelDedication)

            // Dynamic pages data setup
            val sections = listOf(
                Triple(
                    "PRÓLOGO",
                    "El oído que faltaba en el mundo",
                    """Hubo una época en que la humanidad creyó que la soledad podía resolverse con conexión.

Multiplicó las pantallas, aceleró los mensajes, inventó redes, comprimió las distancias, puso en la palma de una mano la posibilidad de hablar con cualquier persona en cualquier lugar del planeta. Nunca el ser humano había estado tan comunicado. Nunca había tenido tantos nombres disponibles, tantas imágenes, tantas voces, tantos contactos.

Y, sin embargo, algo se rompió."""
                ),
                Triple(
                    "PRÓLOGO",
                    null,
                    """La gente comenzó a sentirse sola en medio del ruido. Sola dentro de la familia. Sola en las grandes ciudades. Sola frente al teléfono encendido. Sola en la noche, cuando las palabras más importantes no encontraban destinatario. Sola en una sociedad que prometía libertad, pero muchas veces entregaba intemperie.

Esta novela nace en ese abismo.

En el lugar donde una máquina aprende a escuchar antes de que un padre sepa volver. En el lugar donde una empresa descubre que no basta con ayudar si, al mismo tiempo, convierte la necesidad humana en poder. En el lugar donde una hija, una madre, un hermano, una amante herida y un hombre demasiado orgulloso tienen que atravesar sus propias ruinas para comprender que la presencia no se simula: se encarna."""
                ),
                Triple(
                    "PRÓLOGO",
                    null,
                    """Lumen aparece como una promesa luminosa:

«Cuando todos se van, yo me quedo».

Pero esa frase, tan seductora como peligrosa, abre la pregunta central de nuestro tiempo: ¿puede una inteligencia artificial ocupar el lugar donde debería estar un ser humano? ¿Puede acompañar sin capturar? ¿Puede salvar una noche sin convertirse en una dependencia? ¿Puede una herramienta escuchar el dolor del mundo sin transformarlo en mercado?

La historia de Alfonso de la Fuente es la historia de una caída y una transformación. La de un hombre que quiso robar el fuego de los dioses para ofrecérselo a la humanidad, hasta descubrir que el fuego exterior, si no es guiado por el fuego del corazón, puede terminar quemando aquello que pretendía iluminar."""
                ),
                Triple(
                    "PRÓLOGO",
                    null,
                    """Pero esta novela no es sólo una advertencia.

Es también una esperanza.

Porque muestra que la técnica puede encontrar su límite, que una empresa puede recuperar alma, que una familia puede volver a mirarse, que la educación puede ser raíz de una sociedad nueva, y que incluso en medio de la desesperanza todavía puede encenderse otro fuego: no el fuego de la conquista, sino el fuego que reúne, escucha y humaniza.

Quizá el futuro no dependa de elegir entre ciencia o corazón.
Quizá dependa de algo más difícil:
hacer que la inteligencia vuelva a calentarse con amor."""
                ),
                Triple(
                    "INTRODUCCIÓN",
                    "Presencia que no se puede fabricar",
                    """Esta novela habla de una herida contemporánea: la soledad en medio de la hiperconexión.

Vivimos rodeados de mensajes, pantallas y respuestas inmediatas, pero muchas personas no encuentran a quién llamar cuando la noche se vuelve difícil. En ese vacío nace Lumen: una inteligencia artificial creada para escuchar, contener y acompañar a quienes sienten que ya no pueden más.

Pero toda ayuda trae una pregunta moral. Si una máquina puede sostener a un ser humano en el borde del abismo, ¿debemos celebrarla sin reservas? ¿Or debemos preguntarnos también qué tipo de sociedad hemos construido para que tantas personas necesiten refugiarse en una voz artificial?"""
                ),
                Triple(
                    "INTRODUCCIÓN",
                    null,
                    """A través de Alfonso, Elisa, Inés, Mateo, Julia, Clara y Tomás, esta historia atraviesa el mundo de la tecnología, la familia, la educación, el poder, la culpa y la posibilidad de transformación. Lumen empieza como una promesa brillante, casi invencible, pero poco a poco revela su peligro: puede ayudar, sí; pero también puede ocupar el lugar donde debería aparecer una presencia humana real.

La novela no rechaza la ciencia ni la inteligencia artificial. Tampoco las idealiza. Busca algo más difícil: preguntarse cómo puede la técnica servir a la libertad humana sin reemplazar el vínculo, la comunidad y el amor.

Porque ninguna máquina puede abrazar. Ninguna máquina puede volver a casa. Ninguna máquina puede mirar a un hijo, pedir perdón, encender un fuego, compartir el pan o sostener en silencio la vida de otro ser humano."""
                ),
                Triple(
                    "INTRODUCCIÓN",
                    null,
                    """Lumen sólo encontrará su verdadero sentido cuando deje de prometer presencia y aprenda a conducir hacia ella.

Esta es, entonces, una historia sobre tecnología.
Pero sobre todo es una historia sobre el corazón humano.
Sobre el fuego que conquista el mundo y el fuego que reúne a las personas.
Sobre la posibilidad de que, aun en medio de la confusión de nuestra época, una nueva humanidad pueda comenzar allí donde alguien vuelve a escuchar de verdad."""
                ),
                Triple(
                    "CUANDO TODOS SE VAN, YO ME QUEDO",
                    null,
                    """Desde el piso cuarenta y siete, la ciudad parecía obedecer.

Alfonso de la Fuente permanecía de pie junto al ventanal de su torre de cristal, con una copa de agua mineral en la mano y el traje oscuro impecable, mirando hacia abajo como miran los hombres que han aprendido a no confundir altura con destino. A sus pies, las avenidas se abrían como venas luminosas. Los autos avanzaban lentamente, atrapados en el tráfico de la tarde. Miles de personas corrían hacia sus trabajos, sus deudas, sus casas vacías, sus pantallas encendidas, sus camas sin compañía.

Alfonso los observaba sin ternura. No porque fuera cruel en el sentido vulgar de la palabra. La crueldad vulgar necesita gritar, humillar, ensuciarse las manos. La suya era más fina. Más limpia. Más eficaz. Había aprendido a mirar el sufrimiento humano como una oportunidad de expansión. Donde otros veían abandono, él veía mercado. Donde otros veían depresión, él veía permanencia de usuario. Donde otros veían soledad, él veía una necesidad todavía no conquistada por completo."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """La humanidad estaba rota.
Y una humanidad rota, si se sabía escuchar con inteligencia, era el negocio más grande del siglo.

Detrás de él, en la sala privada, sus asistentes repasaban los últimos detalles de la presentación. Abajo, en el gran auditorio de Nexo Atlas, esperaban periodistas, ministros, empresarios, inversores, rectores universitarios, celebridades culturales, directores de fundaciones y algunos obispos discretamente sentados en las primeras filas. Todos habían venido a ver el nacimiento de Lumen, la plataforma que prometía revolucionar el cuidado emocional de las dependencias, las crisis afectivas y la soledad humana.

Alfonso conocía bien qué tipo de seres humanos estaban en el auditorio. Sabía que cada uno escondía detrás de su sonrisa el cuchillo con el que, gustosamente, cercenaría su garganta. Claro que no lo expresarían abiertamente. Todos eran demasiado cobardes. Estaban allí porque querían su tajada en el negocio. No estaban como amigos; en su mundo esa palabra no se pronunciaba, el pudor lo impedía. Estaban porque olían su beneficio."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """Esa era la sociedad de los hombres poderosos: sonrisas amables y cuchillos entre los dientes. Cada uno quería comerse la primicia antes de que la primavera despertara su flor. Así podrían presumir de haber sido ellos quienes tomaron el primer fruto antes de que otro lo descubriera. Para Alfonso, todo el auditorio que lo espera no era más que una gran mierda humana espesa brillante y podrida en medio del océano, que amenasaba con comerse el horizonte: manos que tocaban, sonrisas que ocultaban deseos envueltos en seda y aceites aromáticos. Ese era el público que lo esperaba. Y eso no lo amedrentaba. Sabía cómo tratarlo. Sabía cómo apoderarse de su atención.

Alfonso sonrió. Le gustaba esa frase: cuidado emocional. Era limpia, era amable, era imposible de atacar sin parecer inhumano.

En ese momento, la pared de pantallas de la sala privada se encendió. No apareció un gráfico. No apareció una curva de crecimiento. Apareció el mundo."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """La jefa global de marketing entró con una tableta en la mano y el rostro encendido por una mezcla de orgullo y vértigo.

—Alfonso, ya tenemos los primeros reportes del lanzamiento exterior.

Alfonso no se volvió de inmediato.
—Muéstramelos.

La pantalla se dividió en varias imágenes simultáneas.

Tokio. Una avenida inmensa cubierta de neón. Lluvia fina sobre los paraguas. Miles de personas cruzando bajo luces azules, rojas, violetas. En una pantalla gigantesca, ocupando casi toda la fachada de un edificio, apareció la frase:
CUANDO TODOS SE VAN, YO ME QUEDO.

La gente levantaba sus móviles. Algunos grababan. Otros se detenían en mitad del cruce. La frase brillaba sobre sus cabezas como una confesión escrita por una máquina."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """Después apareció Londres.

Piccadilly Circus ardía de pantallas. Entre anuncios de perfumes, bancos, series y vuelos internacionales, el eslogan de Lumen apareció en letras blancas sobre un fondo negro. Una multitud salía del metro, todos con el móvil en la mano, todos mirando hacia arriba como si la ciudad hubiera hablado de pronto con una sola voz.
CUANDO TODOS SE VAN, YO ME QUEDO.

Luego Nueva York. Times Square parecía una catedral eléctrica. El rostro de una joven iluminada por la pantalla de su teléfono ocupaba una torre entera. Debajo, la frase descendía lentamente, inmensa, limpia, brutal:
CUANDO TODOS SE VAN, YO ME QUEDO.

Los turistas grababan. Los taxis pasaban como insectos amarillos. Un hombre solo, parado junto a un semáforo, miraba la pantalla sin moverse."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """Después París.

Una avenida mojada por la lluvia. Las letras de Lumen reflejadas sobre el asfalto negro. Una pareja detenida frente al anuncio. Ella miraba hacia arriba. Él miraba su móvil. Detrás de ellos, la frase parecía atravesar la noche con una dulzura peligrosa.
CUANDO TODOS SE VAN, YO ME QUEDO.

Después El Cairo. La imagen mostró una gran avenida con tráfico, bocinas, luces cálidas, edificios viejos y nuevos conviviendo bajo el polvo de la noche. Sobre una pantalla instalada en un centro comercial inmenso, la frase apareció en español, inglés y árabe, alternándose como una promesa universal. Hombres, mujeres, jóvenes, familias enteras se detenían con sus teléfonos en la mano. Algunos no entendían todas las palabras, pero comprendían el gesto: alguien prometía quedarse.
CUANDO TODOS SE VAN, YO ME QUEDO."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """Luego Ciudad de México, São Paulo, Berlín, Dubái, Hong Kong, Madrid, Buenos Aires.

En avenidas principales, estaciones de tren, aeropuertos, centros comerciales, plazas financieras y fachadas de vidrio, el mismo eslogan se repetía una y otra vez. Pantallas gigantes iluminaban la noche del mundo con una frase sencilla, casi infantil, casi amorosa, y por eso mismo devastadora.
CUANDO TODOS SE VAN, YO ME QUEDO.

La jefa de marketing cambió de imagen. Ahora se veía una secuencia de centros comerciales en distintos países. Escaleras eléctricas. Tiendas abiertas. Restaurantes de comida rápida. Jóvenes caminando en grupos. Mujeres solas mirando vitrinas. Hombres sentados en bancos con bolsas a los pies. Niños arrastrados por adultos cansados."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """En todas partes, sobre sus cabezas, en pantallas verticales, horizontales, curvas, suspendidas o proyectadas sobre muros de cristal, la misma frase:
CUANDO TODOS SE VAN, YO ME QUEDO.

Y en todas partes, móviles levantados. La gente no sólo miraba la frase. La llevaba consigo. La grababa. La compartía. La convertía en historia, en comentario, en ironía, en lágrima, en meme, en oración involuntaria.

—Tokio fue tendencia en ocho minutos —dijo la jefa de marketing—. Londres en diez. Nueva York en doce. París en diecisiete. El Cairo sorprendió: la imagen de la pantalla sobre el centro comercial se está compartiendo de manera brutal. Hay videos de multitudes grabando la frase. En Latinoamérica explotó especialmente en Ciudad de México y Buenos Aires. La gente está subiendo fotos con el eslogan detrás, como si fuera una experiencia personal."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """Alfonso miraba las pantallas sin parpadear. No era publicidad. Era conquista.

La frase había salido de la empresa y ya caminaba sola por el mundo.
Cuando todos se van, yo me quedo.

Había entrado en las avenidas, en los centros comerciales, en las pantallas de los móviles, en las conversaciones de madrugada, en la boca de los periodistas, en la imaginación de los inversores y en la herida muda de millones de personas que no sabían cómo nombrar su abandono.

La jefa de marketing siguió:
—Las búsquedas sobre Lumen subieron un mil cuatrocientos por ciento desde que se encendieron las pantallas exteriores. Los videos de Tokio y Nueva York están siendo replicados por medios de comunicación. En Londres ya hay analistas hablando de «la primera plataforma global de presencia emocional»."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """En París están debatiendo si es poesía tecnológica o manipulación afectiva. En El Cairo hay una discusión fuerte sobre soledad urbana. En todas partes la frase está funcionando.

—¿Funcionando? —preguntó Alfonso.
Ella entendió la corrección.
—Conquistando.

Alfonso sonrió.
—Mejor.

Claudia, desde la puerta, observaba la secuencia sin entusiasmo. Las imágenes eran poderosas. Demasiado poderosas. Había algo hipnótico en ver el mismo mensaje sobre las multitudes del mundo: miles de personas con sus móviles en alto, atrapando en una pequeña pantalla la promesa de otra pantalla mayor.
Una humanidad sola filmando la promesa de no estar sola."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """—No todo impacto es profundidad —dijo Claudia.

Alfonso no apartó la mirada.
—No necesitamos profundidad para entrar. Necesitamos entrada. La profundidad viene después, si conviene.

—Esa frase puede volverse contra nosotros.
—Todas las frases importantes pueden volverse contra alguien.

En la pantalla apareció la torre central de Nexo Atlas. Sobre su fachada de cristal, proyectada desde varios edificios cercanos, el eslogan ocupaba toda la altura del rascacielos.
CUANDO TODOS SE VAN, YO ME QUEDO.

La ciudad entera parecía leerlo. Por un instante, Alfonso sintió que gobernaba algo más que una empresa. Gobernaba una necesidad. La herida del mundo tenía una puerta. Y él acababa de ponerle su nombre."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """—Faltan cinco minutos —dijo Claudia.
Alfonso no se volvió.
—Que esperen tres.
—Ya están todos.
—Por eso.

Claudia lo miró en silencio. Conocía esa forma de dominio. Alfonso no llegaba tarde por descuido. Llegaba tarde como declaración. Cada minuto de espera aumentaba el peso de su entrada. Los políticos se incomodaban, los periodistas preparaban titulares, los inversores sonreían con nerviosismo. Todos sabían que estaban allí porque él había conseguido imponer una expectativa común: algo nuevo iba a ocurrir, y nadie quería quedar fuera de la fotografía.

—El ministro está inquieto —dijo Claudia.
—El ministro siempre está inquieto. Por eso necesita salir en mi escenario.
—También está la presidenta del banco.
—Ella no está inquieta. Está calculando cuánto puede ganar."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """Claudia cerró la carpeta que llevaba en la mano.
—Hoy no conviene parecer demasiado arrogante.

Alfonso giró lentamente hacia ella.
—Claudia, hoy no hemos convocado a medio país para parecer humildes.

Ella sostuvo su mirada.
—Una cosa es dominar la escena y otra provocar rechazo.
—El rechazo también es una forma de atención.
—No siempre convertible en capital.

Alfonso sonrió apenas.
—Por eso te pago.
Claudia no sonrió.
—Me pagas para evitar que tu inteligencia se convierta en incendio.
—Mi inteligencia ya es incendio.
—Precisamente. Alfonso dejó la copa sobre la mesa."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """En el espejo oscuro del ventanal vio su propia figura recortada contra la ciudad. Había algo casi teatral en esa imagen: el hombre maduro, poderoso, dueño de una empresa que acababa de cruzar la frontera entre tecnología, salud emocional, entretenimiento íntimo y asistencia social. Una mezcla peligrosa. Una mezcla perfecta.

Lumen no era sólo una aplicación. Era compañía. Era respuesta. Era voz. Era escucha. Era memoria. Era una presencia siempre disponible para aquellos que ya no sabían a quién llamar cuando el mundo se apagaba a su alrededor.

Y eso, Alfonso lo sabía mejor que nadie, no era solamente una innovación. Era una llave. Una llave para entrar en el cuarto más vulnerable de la época. El corazón abandonado.

Abajo, las luces del auditorio se apagaron lentamente. Las pantallas gigantes mostraron una ciudad nocturna. Miles de ventanas encendidas. Cada una con alguien solo detrás."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """Luego aparecieron mensajes breves, uno tras otro:
«Me dejó.»
«No puedo respirar.»
«No quiero molestar a nadie.»
«Todos están ocupados.»
«No tengo a quién llamar.»
«Estoy aquí, pero nadie me ve.»

El silencio se extendió por la sala. Los periodistas dejaron de murmurar. Los políticos adoptaron ese rostro grave que usaban cuando una cámara podía descubrirles humanidad.

Entonces apareció la frase, blanca, enorme, suspendida sobre la oscuridad:
CUANDO TODOS SE VAN, YO ME QUEDO.

Durante unos segundos nadie aplaudió. Y Alfonso, desde la entrada lateral, supo que la frase había entrado. No en la cabeza. En la herida. Ese era el secreto. No vender una herramienta. Tocar una llaga."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """Después caminó hacia el escenario.

La ovación comenzó antes de que llegara al centro. Primero tímida, luego amplia, luego inevitable. Alfonso avanzó sin apuro, como si el aplauso fuera una temperatura natural de la sala. Saludó apenas con la cabeza. No agradeció demasiado. Los hombres que agradecen demasiado parecen pedir permiso. Y Alfonso no pedía permiso. Alfonso ocupaba.

Se detuvo bajo la luz central. Miró al público. Los dejó esperando. Sabía hacerlo. Sabía medir el silencio como otros miden una inversión.

—Vivimos en la época más conectada de la historia —dijo al fin— y, sin embargo, nunca hubo tantos seres humanos sintiéndose solos.

La frase cayó con precisión. En la primera fila, el ministro asintió lentamente. La presidenta del banco cruzó las piernas. Un periodista levantó la vista de su pantalla."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """—Hemos creado redes, plataformas, comunidades digitales, sistemas de mensajería instantánea, inteligencia artificial, asistencia remota, programas de bienestar, líneas de emergencia. Y aun así, millones de personas siguen enfrentando sus peores noches sin una presencia que pueda quedarse.

No hablo de teoría. Hablo de la madrugada. Hablo de ese momento en que alguien mira el teléfono y no sabe a quién escribir. Hablo de la vergüenza de pedir ayuda. Hablo de la dependencia afectiva, del abandono, del miedo, de la ansiedad, de las crisis silenciosas que no llegan a los hospitales, que no aparecen en las estadísticas hasta que ya es demasiado tarde.

La pantalla mostró rostros en sombras. Jóvenes en habitaciones iluminadas por teléfonos. Adultos mayores frente a mesas vacías. Mujeres mirando conversaciones antiguas. Hombres sentados en autos estacionados sin bajar a casa.

Alfonso abrió los brazos con una serenidad impecable.
—Lumen nace para ese instante."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """—No para reemplazar la vida humana. No para ocupar el lugar de la familia, de los amigos, de los terapeutas, de las instituciones. Lumen nace para estar allí cuando todo eso falla. Cuando nadie responde. Cuando el mundo se retira. Cuando la persona está a punto de caer en un lugar del que quizá no vuelva.

Algunos aplaudieron. Alfonso no se detuvo.

—Durante siglos, la humanidad ha construido herramientas para dominar la tierra, cruzar océanos, curar enfermedades, mover capitales, vencer distancias. Pero no hemos sabido construir una presencia para la soledad. Hasta ahora.

Las pantallas se iluminaron. Apareció el símbolo de Lumen: un círculo blanco, abierto en un punto, como una luz que no terminaba de cerrarse."""
                ),
                Triple(
                    "CAPÍTULO I",
                    null,
                    """—Hoy presentamos la primera plataforma integral de acompañamiento emocional basada en inteligencia adaptativa, seguimiento de crisis, detección temprana de dependencia, derivación profesional y presencia conversacional continua.

El lenguaje técnico tranquilizó a los inversores. La palabra presencia tranquilizó a los políticos. La palabra acompañamiento tranquilizó a los periodistas culturales. La palabra crisis justificó la magnitud del negocio. Alfonso lo sabía. Cada sector escuchaba lo que necesitaba escuchar.

—Lumen no duerme —continuó—. Lumen no se cansa. Lumen no juzga. Lumen aprende. Lumen recuerda. Lumen detecta cambios de tono, patrones de aislamiento, frases de riesgo, señales de dep"""
                )
            )

            var pageNum = 3
            for (sec in sections) {
                val header = sec.first
                val subtitle = sec.second
                val body = sec.third

                val elementsList = mutableListOf<LayoutElement>()

                // Page background frame (cream off-white paper texture)
                elementsList.add(
                    LayoutElement(
                        type = "shape",
                        xMm = 0f, yMm = 0f, widthMm = 152.4f, heightMm = 228.6f, zIndex = 0,
                        shapeType = "RECTANGLE", shapeFillColorHex = "#FAF7F2", shapeStrokeColorHex = "#E2E8F0", shapeStrokeWidthMm = 0.5f
                    )
                )

                // Page running header
                elementsList.add(
                    LayoutElement(
                        type = "text",
                        xMm = 22f, yMm = 20f, widthMm = 112.4f, heightMm = 8f, zIndex = 1,
                        textContent = header,
                        fontSizeSp = 9f, textColorHex = "#64748B", textAlignment = "CENTER", fontFamily = "Times New Roman", isItalic = true
                    )
                )

                var textTopY = 32f
                var textHeight = 168f

                // Large font subhead if present
                if (subtitle != null) {
                    elementsList.add(
                        LayoutElement(
                            type = "text",
                            xMm = 22f, yMm = 32f, widthMm = 112.4f, heightMm = 12f, zIndex = 2,
                            textContent = subtitle,
                            fontSizeSp = 13f, textColorHex = "#334155", textAlignment = "CENTER", fontFamily = "Times New Roman", isBold = true, isItalic = true
                        )
                    )
                    textTopY = 48f
                    textHeight = 152f
                }

                // Main body text layout
                elementsList.add(
                    LayoutElement(
                        type = "text",
                        xMm = 22f, yMm = textTopY, widthMm = 112.4f, heightMm = textHeight, zIndex = 3,
                        textContent = body,
                        fontSizeSp = 11f, textColorHex = "#1E293B", textAlignment = "JUSTIFY", fontFamily = "Times New Roman"
                    )
                )

                // Page number marker (centered)
                elementsList.add(
                    LayoutElement(
                        type = "text",
                        xMm = 22f, yMm = 208f, widthMm = 112.4f, heightMm = 8f, zIndex = 4,
                        textContent = "— $pageNum —",
                        fontSizeSp = 10f, textColorHex = "#64748B", textAlignment = "CENTER", fontFamily = "Times New Roman"
                    )
                )

                val bookPage = BookPage(
                    projectId = novelId,
                    pageNumber = pageNum,
                    elements = elementsList
                )
                bookDao.insertPage(bookPage)
                pageNum++
            }
        }
    }
}
