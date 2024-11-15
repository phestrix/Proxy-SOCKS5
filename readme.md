# Проект прокси на протоколе SOCKS5
### Описание задачи:
* Необходимо реализовать прокси-сервер, соответствующий стандарту SOCKS версии 5.
* В параметрах программе передаётся только порт, на котором прокси будет ждать входящих подключений от клиентов.
* Из трёх доступных в протоколе команд, обязательной является только реализация команды 1 (establish a TCP/IP stream connection)
* Поддержку аутентификации и IPv6-адресов реализовывать не требуется.
* Для реализации прокси использовать неблокирующиеся сокеты, работая с ними в рамках одного треда. Дополнительные треды использовать не допускается. Соответственно, никаких блокирующихся вызовов (кроме вызова селектора) не допускается.
* Прокси должен поддерживать резолвинг доменных имён (значение 0x03 в поле address)

### Для сборки проекта нужен установленный сборщик Gradle
### Для сборки выполните команду в папке проекта gradlew build
В полученных архивах найдите в папке bin файл c расширением .bat для Windows или исполняемый файл для UNIX систем и запустите
Прокси будет работать на адресе localhost:5001