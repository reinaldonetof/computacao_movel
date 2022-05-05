<!doctype html>
<html>
<head>
<meta charset="UTF-8">
<title> Broker Computacao Móvel</title>
</head>
<body>

<div>
    <ul id="log">

    </ul>
</div>

<script src="https://code.jquery.com/jquery-3.6.0.min.js" integrity="sha256-/xUj+3OJU5yExlq6GSYGSHk7tPXikynS7ogEvDej/m4="
  crossorigin="anonymous">
</script>

<script>

    $(function(){
        setInterval(function(){
            $.getJSON( "./getLog.php", function( data ) {
              var $log = $('#log');
              $.each( data, function( key, val ) {
                $log.prepend( "<li>" + val + "</li>" );
              });
            });

        },5000);
    });

</script>

</body>
</html>
