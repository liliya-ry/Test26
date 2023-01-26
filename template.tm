<html>
<body>
<span size=12 page=1 t:if="${welcome.message}" t:text="Welcome" />
<span t:if="${welcome.num}" t:text="Hello" />
<span t:text="#{welcome.message}" />
<table>
    <tr t:each="student: ${students}">
      <td t:text="${student.id}" />
      <td t:text="${student.name}" />
      <td>
          <tr t:each="student: ${students}">
            <td t:text="${student.id}" />
            <td t:text="${student.name}" />
          </tr>
      </td>
    </tr>
</table>
</body>
</html>